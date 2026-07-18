package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

func GetAllListings(c *gin.Context) {
	userID, _ := c.Get("userID")
	search := c.PostForm("search")
	offset, _ := strconv.Atoi(c.DefaultPostForm("offset", "0"))
	seed := c.DefaultPostForm("seed", fmt.Sprintf("%v%s", userID, time.Now().Format("20060102")))

	query := `
    WITH impression_counts AS (
        SELECT listing_id, COUNT(*) as times_shown_recently
        FROM listing_impressions
        WHERE user_id = $1
         AND shown_at > NOW() - INTERVAL '24 hours'
        GROUP BY listing_id
    ),
    scored AS (
        SELECT l.id,
               l.seller_id,
               u.username as seller_name,
               TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
               t.common_name,
               l.price,
               l.description,
               l.image_url,
               l.sex,
               l.status,
               l.listed_time,
               u.subscription_tier,
               u.whatsapp,
               u.facebook,
               u.instagram,
               (((CASE COALESCE(u.subscription_tier, 0)
                    WHEN 2 THEN 5
                    WHEN 1 THEN 2
                    ELSE 1
                END)
               + EXP(-LN(2) * EXTRACT(EPOCH FROM (NOW() - COALESCE(l.listed_time, NOW()))) / 86400.0
                     / (CASE COALESCE(u.subscription_tier, 0)
                            WHEN 2 THEN 5
                            WHEN 1 THEN 2
                            ELSE 1
                        END)))
               / POWER(1 + COALESCE(ic.times_shown_recently, 0), 0.15))
               * (CASE WHEN $2::text IS NOT NULL
                       THEN (POWER(similarity(t.common_name, $2::text), 3) * 50.0 + 1.0)
                       ELSE 1.0
                  END) as exposure_score
        FROM listings l
        JOIN taxa t ON l.species_lsid = t.species_lsid
        JOIN users u ON l.seller_id = u.id
        LEFT JOIN impression_counts ic ON ic.listing_id = l.id
        WHERE l.status = 'active'`

	params := []any{userID, search, seed}
	paramCount := 4

	if search != "" {
		query += fmt.Sprintf(" AND (t.common_name %% $2 OR t.genus %% $2 OR t.species %% $2 OR t.common_name ILIKE $%d)", paramCount)
		params = append(params, "%"+search+"%")
		paramCount++
	}

	query += fmt.Sprintf(`
    ),
    randomized AS (
        SELECT *,
               (ABS(('x' || SUBSTR(MD5(id::text || $3), 1, 8))::bit(32)::integer)::double precision / 2147483647.0)
               ^ (1.0 / exposure_score) as probability
        FROM scored
    )
    SELECT * FROM randomized
    ORDER BY probability DESC
    LIMIT 10 OFFSET $%d`, paramCount)

	params = append(params, offset)

	rows, err := db.Pool.Query(context.Background(), query, params...)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch listings", nil)
		return
	}
	defer rows.Close()

	listings := []map[string]any{}
	ids := []int64{}


	for rows.Next() {
		var (
			id, subscriptionTier                                        int
			sellerID, sellerName, scientificName, description           string
			imageURL, sex, status                                       string
			commonName, whatsapp, facebook, instagram                   *string
			price                                                       *float64
			listedTime                                                  time.Time
			exposureScore, probability                                  float64
		)

		if err := rows.Scan(&id, &sellerID, &sellerName, &scientificName, &commonName, &price,
			&description, &imageURL, &sex, &status, &listedTime, &subscriptionTier,
			&whatsapp, &facebook, &instagram, &exposureScore, &probability); err != nil {
			continue
		}

		displayCommonName := scientificName
		if commonName != nil && *commonName != "" {
			displayCommonName = *commonName
		}

		priceDisplay := "Contact"
		if price != nil {
			priceDisplay = fmt.Sprintf("R %.2f", *price)
		}

		listings = append(listings, map[string]any{
			"id":                id,
			"seller_id":         sellerID,
			"seller_name":       sellerName,
			"scientific_name":   scientificName,
			"common_name":       displayCommonName,
			"price":             priceDisplay,
			"description":       description,
			"image_url":         imageURL,
			"sex":               sex,
			"status":            status,
			"listed_time":       listedTime,
			"exposure_score":    exposureScore,
			"probability":       probability,
			"subscription_tier": subscriptionTier,
			"whatsapp":          whatsapp,
			"facebook":          facebook,
			"instagram":         instagram,
		})
		ids = append(ids, int64(id))
	}

	if len(ids) > 0 {
		if err := db.LogImpressions(context.Background(), fmt.Sprintf("%v", userID), ids, "listing_impressions"); err != nil {
			// Impression logging failure shouldn't fail the listings response
		}
	}

	utils.SendSuccess(c, "Listings fetched", map[string]any{"listings": listings})
}

func CreateListing(c *gin.Context) {
	userID, _ := c.Get("userID")

	speciesLSID := c.PostForm("species_lsid")
	priceStr := c.PostForm("price")
	description := c.DefaultPostForm("description", "")
	sex := c.DefaultPostForm("sex", "Unsexed")
	imageBase64 := c.PostForm("image_data")

	if speciesLSID == "" || priceStr == "" {
		utils.SendError(c, http.StatusBadRequest, "Required fields missing", nil)
		return
	}

	price, err := strconv.ParseFloat(priceStr, 64)
	if err != nil {
		utils.SendError(c, http.StatusBadRequest, "Price must be numeric", nil)
		return
	}

	imageURL, _ := utils.SaveBase64Image(imageBase64, "listings")

	var listingID int
	query := `INSERT INTO listings (seller_id, species_lsid, price, description, sex, image_url)
              VALUES ($1, $2, $3, $4, $5, $6) RETURNING id`
	err = db.Pool.QueryRow(context.Background(), query, userID, speciesLSID, price, description, sex, imageURL).Scan(&listingID)

	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to create listing", nil)
		return
	}

	utils.SendSuccess(c, "Listing created successfully", map[string]any{"listing_id": listingID})
}

func UpdateListing(c *gin.Context) {
	userID, _ := c.Get("userID")

	listingID := c.PostForm("listing_id")
	priceStr := c.PostForm("price")
	description := c.PostForm("description")
	status := c.PostForm("status")
	sex := c.PostForm("sex")

	if listingID == "" {
		utils.SendError(c, http.StatusBadRequest, "Listing ID required", nil)
		return
	}

	// Check ownership
	var sellerID string
	err := db.Pool.QueryRow(context.Background(), "SELECT seller_id FROM listings WHERE id = $1", listingID).Scan(&sellerID)
	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Listing not found", nil)
		return
	}
	if sellerID != userID {
		utils.SendError(c, http.StatusForbidden, "Unauthorized to update this listing", nil)
		return
	}

	updates := []string{}
	params := []any{listingID}

	if priceStr != "" {
		price, err := strconv.ParseFloat(priceStr, 64)
		if err == nil {
			updates = append(updates, fmt.Sprintf("price = $%d", len(params)+1))
			params = append(params, price)
		}
	}
	if description != "" {
		updates = append(updates, fmt.Sprintf("description = $%d", len(params)+1))
		params = append(params, description)
	}
	if status != "" {
		updates = append(updates, fmt.Sprintf("status = $%d", len(params)+1))
		params = append(params, status)
	}
	if sex != "" {
		updates = append(updates, fmt.Sprintf("sex = $%d", len(params)+1))
		params = append(params, sex)
	}

	if len(updates) == 0 {
		utils.SendError(c, http.StatusBadRequest, "No fields to update", nil)
		return
	}

	query := fmt.Sprintf("UPDATE listings SET %s WHERE id = $1", strings.Join(updates, ", "))
	_, err = db.Pool.Exec(context.Background(), query, params...)

	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to update listing", nil)
		return
	}

	utils.SendSuccess(c, "Listing updated successfully", nil)
}

func DeleteListing(c *gin.Context) {
	userID, _ := c.Get("userID")
	listingID := c.PostForm("listing_id")

	if listingID == "" {
		utils.SendError(c, http.StatusBadRequest, "Listing ID required", nil)
		return
	}

	var sellerID string
	err := db.Pool.QueryRow(context.Background(), "SELECT seller_id FROM listings WHERE id = $1", listingID).Scan(&sellerID)
	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Listing not found", nil)
		return
	}
	if sellerID != userID {
		utils.SendError(c, http.StatusForbidden, "Unauthorized to delete this listing", nil)
		return
	}

	_, err = db.Pool.Exec(context.Background(), "DELETE FROM listings WHERE id = $1", listingID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to delete listing", nil)
		return
	}

	utils.SendSuccess(c, "Listing deleted successfully", nil)
}

func GetListingDetails(c *gin.Context) {
	userID, _ := c.Get("userID")
	listingID := c.DefaultPostForm("id", c.Query("id"))

	if listingID == "" {
		utils.SendError(c, http.StatusBadRequest, "Listing ID required", nil)
		return
	}

	query := `
        SELECT l.*,
               u.username as seller_name,
               u.subscription_tier,
               u.whatsapp, u.facebook, u.instagram,
               t.genus, t.species, t.common_name
        FROM listings l
        JOIN users u ON l.seller_id = u.id
        JOIN taxa t ON l.species_lsid = t.species_lsid
        WHERE l.id = $1`

	row := db.Pool.QueryRow(context.Background(), query, listingID)

	var (
		id, subscriptionTier                               int
		sellerID, speciesLSID, sellerName, desc            string
		imageURL, sex, status, genus, species              string
		whatsapp, facebook, instagram, commonName          *string
		price                                              *float64
		listedTime                                         time.Time
	)

	err := row.Scan(&id, &sellerID, &speciesLSID, &price, &desc, &imageURL, &sex, &status, &listedTime,
		&sellerName, &subscriptionTier, &whatsapp, &facebook, &instagram, &genus, &species, &commonName)

	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Listing not found", nil)
		return
	}

	// Log impression
	db.LogImpressions(context.Background(), fmt.Sprintf("%v", userID), []int64{int64(id)}, "listing_impressions")

	displayCommonName := genus + " " + species
	if commonName != nil && *commonName != "" {
		displayCommonName = *commonName
	}

	priceFormatted := "Contact"
	if price != nil {
		priceFormatted = fmt.Sprintf("R %.2f", *price)
	}

	utils.SendSuccess(c, "Listing details fetched", map[string]any{
		"id":                id,
		"seller_id":         sellerID,
		"seller_name":       sellerName,
		"species_lsid":      speciesLSID,
		"price":             price,
		"price_formatted":   priceFormatted,
		"description":       desc,
		"image_url":         imageURL,
		"sex":               sex,
		"status":            status,
		"listed_time":       listedTime,
		"subscription_tier": subscriptionTier,
		"whatsapp":          whatsapp,
		"facebook":          facebook,
		"instagram":         instagram,
		"genus":             genus,
		"species":           species,
		"common_name":       displayCommonName,
	})
}

func GetAllSpecies(c *gin.Context) {
	query := `
        SELECT s.speciesId as id,
               TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientificName,
               t.common_name as commonName,
               s.family,
               t.species_lsid as speciesLsid
        FROM taxa t
        JOIN spiders s ON t.species_lsid = s.species_lsid`

	rows, err := db.Pool.Query(context.Background(), query)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch species", nil)
		return
	}
	defer rows.Close()

	speciesList := []map[string]any{}
	for rows.Next() {
		var id int
		var scientificName, speciesLsid string
		var commonName, family *string

		if err := rows.Scan(&id, &scientificName, &commonName, &family, &speciesLsid); err != nil {
			continue
		}

		speciesList = append(speciesList, map[string]any{
			"id":             id,
			"scientificName": scientificName,
			"commonName":     commonName,
			"family":         family,
			"order":          "Araneae",
			"speciesLsid":    speciesLsid,
		})
	}

	utils.SendSuccess(c, "Species fetched", map[string]any{"species": speciesList})
}
