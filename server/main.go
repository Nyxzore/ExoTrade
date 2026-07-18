package main

import (
	"exotrade-server/internal/api/handlers"
	"exotrade-server/internal/api/middleware"
	"exotrade-server/internal/db"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
)

func main() {
	// Load environment variables
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, using system environment variables")
	}

	// Initialize Database
	if err := db.InitDB(); err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer db.CloseDB()

	r := gin.Default()

	// Legacy Static Page
	r.StaticFile("/get-app.php", "./get-app.html")
	r.StaticFile("/exotrade-api-docs.html", "./exotrade-api-docs.html")

	// API Routes
	api := r.Group("/")
	{
		// Obsidian Graph (Public for now)
		api.GET("/get_graph_data.php", handlers.GetGraphData)
		api.GET("/get_note_content.php", handlers.GetNoteContent)

		// Core
		api.GET("/core/get_versions.php", handlers.GetVersions)

		// Auth
		api.POST("/auth/auth.php", handlers.AuthHandler)

		// Protected Routes
		protected := api.Group("/")
		protected.Use(middleware.AppVersionCheck())
		protected.Use(middleware.AuthRequired())
		{
			protected.POST("/core/report_item.php", handlers.ReportItem)
			protected.POST("/profile/get_profile.php", handlers.GetProfile)
			protected.POST("/profile/update_profile.php", handlers.UpdateProfile)
			protected.POST("/listings/create_listing.php", handlers.CreateListing)
			protected.POST("/listings/update_listing.php", handlers.UpdateListing)
			protected.POST("/listings/delete_listing.php", handlers.DeleteListing)
			protected.GET("/listings/get_listing_details.php", handlers.GetListingDetails)
			protected.POST("/listings/get_listing_details.php", handlers.GetListingDetails)
			protected.GET("/listings/get_all_species.php", handlers.GetAllSpecies)
			protected.POST("/listings/get_all_listings.php", handlers.GetAllListings)
			protected.POST("/breeding/create_breeding_listing.php", handlers.CreateBreedingListing)
			protected.POST("/breeding/update_breeding_listing.php", handlers.UpdateBreedingListing)
			protected.POST("/breeding/delete_breeding_listing.php", handlers.DeleteBreedingListing)
			protected.GET("/breeding/get_breeding_listing_details.php", handlers.GetBreedingListingDetails)
			protected.POST("/breeding/get_breeding_listing_details.php", handlers.GetBreedingListingDetails)
			protected.POST("/breeding/get_my_breeding_status.php", handlers.GetMyBreedingStatus)
			protected.POST("/breeding/find_breeding_matches.php", handlers.FindBreedingMatches)
			protected.POST("/breeding/get_breeding_listings.php", handlers.GetBreedingListings)
			protected.GET("/friends/get_friends.php", handlers.GetFriends)
			protected.POST("/friends/send_friend_request.php", handlers.SendFriendRequest)
			protected.POST("/friends/accept_friend_request.php", handlers.AcceptFriendRequest)
			protected.POST("/friends/decline_friend_request.php", handlers.DeclineFriendRequest)
			protected.POST("/friends/remove_friend.php", handlers.RemoveFriend)
			protected.POST("/friends/get_friend_requests.php", handlers.GetFriendRequests)
			protected.POST("/friends/search_users.php", handlers.SearchUsers)
			protected.GET("/messaging/get_conversations.php", handlers.GetConversations)
			protected.POST("/messaging/get_conversations.php", handlers.GetConversations)
			protected.GET("/messaging/get_messages.php", handlers.GetMessages)
			protected.POST("/messaging/get_messages.php", handlers.GetMessages)
			protected.POST("/messaging/mark_read.php", handlers.MarkRead)
			protected.POST("/messaging/start_or_get_conversation.php", handlers.StartOrGetConversation)
			protected.POST("/messaging/get_backup.php", handlers.GetBackup)
			protected.POST("/messaging/send_message.php", handlers.SendMessage)

			// Notifications (User-facing but in admin/ folder in legacy)
			protected.GET("/admin/get_notifications.php", handlers.GetNotifications)
			protected.POST("/admin/get_notifications.php", handlers.GetNotifications)

			// Admin Routes
			admin := protected.Group("/")
			admin.Use(middleware.AdminRequired())
			{
				admin.POST("/admin/get_flagged_items.php", handlers.GetFlaggedItems)
				admin.POST("/admin/resolve_report.php", handlers.ResolveReport)
				admin.POST("/admin/take_down_listing.php", handlers.TakeDownListing)
				admin.POST("/admin/ban_user.php", handlers.BanUser)
			}
		}
	}

	// Static files fallback (avoids wildcard conflicts)
	r.NoRoute(func(c *gin.Context) {
		path := c.Request.URL.Path
		for _, dir := range []string{"listings", "profile_pics", "downloads"} {
			if strings.HasPrefix(path, "/"+dir+"/") {
				file := filepath.Join(".", path)
				if _, err := os.Stat(file); err == nil {
					c.File(file)
					return
				}
			}
		}
		c.JSON(http.StatusNotFound, gin.H{"status": "error", "message": "Route not found"})
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("Server starting on port %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
