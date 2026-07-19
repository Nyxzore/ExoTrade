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

	// Trust only localhost as a proxy (recommended for cloudflared on same machine)
	r.SetTrustedProxies([]string{"127.0.0.1"})

	// Legacy Static Page
	r.StaticFile("/", "./index.html")
	r.StaticFile("/logo.png", "./logo.png")
	r.StaticFile("/get-app", "./get-app.html")

	// Desktop Web App
	r.Static("/desktop", "./desktop/browser_app")

	// Deep Link / Share redirects
	r.GET("/listing/:id", func(c *gin.Context) {
		c.File("./get-app.html")
	})
	r.GET("/breeding/:id", func(c *gin.Context) {
		c.File("./get-app.html")
	})

	// API Routes
	api := r.Group("/")
	{
		// Core
		api.GET("/core/get_versions", handlers.GetVersions)

		// Auth
		api.POST("/auth/auth", handlers.AuthHandler)
		api.POST("/auth/forgot-password", handlers.ForgotPasswordHandler)
		api.POST("/auth/reset-password", handlers.ResetPasswordHandler)

		// Protected Routes
		protected := api.Group("/")
		protected.Use(middleware.AppVersionCheck())
		protected.Use(middleware.AuthRequired())
		{
			// Obsidian Graph (Authenticated only)
			protected.GET("/get_graph_data", handlers.GetGraphData)
			protected.GET("/get_note_content", handlers.GetNoteContent)

			protected.POST("/core/report_item", handlers.ReportItem)
			protected.POST("/profile/get_profile", handlers.GetProfile)
			protected.POST("/profile/update_profile", handlers.UpdateProfile)
			protected.POST("/listings/create_listing", handlers.CreateListing)
			protected.POST("/listings/update_listing", handlers.UpdateListing)
			protected.POST("/listings/delete_listing", handlers.DeleteListing)
			protected.GET("/listings/get_listing_details", handlers.GetListingDetails)
			protected.POST("/listings/get_listing_details", handlers.GetListingDetails)
			protected.GET("/listings/get_all_species", handlers.GetAllSpecies)
			protected.POST("/listings/get_all_listings", handlers.GetAllListings)
			protected.POST("/breeding/create_breeding_listing", handlers.CreateBreedingListing)
			protected.POST("/breeding/update_breeding_listing", handlers.UpdateBreedingListing)
			protected.POST("/breeding/delete_breeding_listing", handlers.DeleteBreedingListing)
			protected.GET("/breeding/get_breeding_listing_details", handlers.GetBreedingListingDetails)
			protected.POST("/breeding/get_breeding_listing_details", handlers.GetBreedingListingDetails)
			protected.POST("/breeding/get_my_breeding_status", handlers.GetMyBreedingStatus)
			protected.POST("/breeding/find_breeding_matches", handlers.FindBreedingMatches)
			protected.POST("/breeding/get_breeding_listings", handlers.GetBreedingListings)
			protected.POST("/friends/get_friends", handlers.GetFriends)
			protected.POST("/friends/send_friend_request", handlers.SendFriendRequest)
			protected.POST("/friends/accept_friend_request", handlers.AcceptFriendRequest)
			protected.POST("/friends/decline_friend_request", handlers.DeclineFriendRequest)
			protected.POST("/friends/remove_friend", handlers.RemoveFriend)
			protected.POST("/friends/get_friend_requests", handlers.GetFriendRequests)
			protected.POST("/friends/search_users", handlers.SearchUsers)
			protected.GET("/messaging/get_conversations", handlers.GetConversations)
			protected.POST("/messaging/get_conversations", handlers.GetConversations)
			protected.GET("/messaging/get_messages", handlers.GetMessages)
			protected.POST("/messaging/get_messages", handlers.GetMessages)
			protected.POST("/messaging/mark_read", handlers.MarkRead)
			protected.POST("/messaging/start_or_get_conversation", handlers.StartOrGetConversation)
			protected.POST("/messaging/get_backup", handlers.GetBackup)
			protected.POST("/messaging/send_message", handlers.SendMessage)
			protected.POST("/messaging/upload_attachment", handlers.UploadAttachment)

			// Notifications (User-facing but in admin/ folder in legacy)
			protected.GET("/admin/get_notifications", handlers.GetNotifications)
			protected.POST("/admin/get_notifications", handlers.GetNotifications)

			// Admin Routes
			admin := protected.Group("/")
			admin.Use(middleware.AdminRequired())
			{
				admin.POST("/admin/get_flagged_items", handlers.GetFlaggedItems)
				admin.POST("/admin/resolve_report", handlers.ResolveReport)
				admin.POST("/admin/take_down_listing", handlers.TakeDownListing)
				admin.POST("/admin/ban_user", handlers.BanUser)
			}
		}
	}

	// Static files fallback (avoids wildcard conflicts)
	r.NoRoute(func(c *gin.Context) {
		path := c.Request.URL.Path
		for _, dir := range []string{"listings", "profile_pics", "downloads", "chat_attachments"} {
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
