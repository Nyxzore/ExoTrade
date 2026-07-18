# Walkthrough - PHP to Go Porting

I have successfully ported all remaining PHP endpoints to the Go backend, achieving behavioral parity with the legacy implementation.

## Ported Endpoints

The following endpoints have been implemented in Go and registered in `main.go`:

### Core Domain
- `GET /core/get_versions.php` -> `handlers.GetVersions`
- `POST /core/report_item.php` -> `handlers.ReportItem`
- `GET /get-app.php` -> Static HTML page `get-app.html`

### Profile Domain
- `POST /profile/get_profile.php` -> `handlers.GetProfile`
- `POST /profile/update_profile.php` -> `handlers.UpdateProfile` (Handles base64 images and security backup rotation)

### Listings Domain
- `POST /listings/create_listing.php` -> `handlers.CreateListing`
- `POST /listings/update_listing.php` -> `handlers.UpdateListing`
- `POST /listings/delete_listing.php` -> `handlers.DeleteListing`
- `GET/POST /listings/get_listing_details.php` -> `handlers.GetListingDetails`
- `GET /listings/get_all_species.php` -> `handlers.GetAllSpecies` (New: Provides detailed species info from joined `taxa` and `spiders` tables)

### Breeding Domain
- `POST /breeding/create_breeding_listing.php` -> `handlers.CreateBreedingListing`
- `POST /breeding/update_breeding_listing.php` -> `handlers.UpdateBreedingListing`
- `POST /breeding/delete_breeding_listing.php` -> `handlers.DeleteBreedingListing`
- `GET/POST /breeding/get_breeding_listing_details.php` -> `handlers.GetBreedingListingDetails`
- `POST /breeding/get_my_breeding_status.php` -> `handlers.GetMyBreedingStatus`
- `POST /breeding/find_breeding_matches.php` -> `handlers.FindBreedingMatches`

### Friends Domain
- `POST /friends/send_friend_request.php` -> `handlers.SendFriendRequest`
- `POST /friends/accept_friend_request.php` -> `handlers.AcceptFriendRequest`
- `POST /friends/decline_friend_request.php` -> `handlers.DeclineFriendRequest`
- `POST /friends/remove_friend.php` -> `handlers.RemoveFriend`
- `POST /friends/get_friend_requests.php` -> `handlers.GetFriendRequests`
- `POST /friends/search_users.php` -> `handlers.SearchUsers`

### Messaging Domain
- `GET/POST /messaging/get_conversations.php` -> `handlers.GetConversations`
- `GET/POST /messaging/get_messages.php` -> `handlers.GetMessages`
- `POST /messaging/mark_read.php` -> `handlers.MarkRead`
- `POST /messaging/start_or_get_conversation.php` -> `handlers.StartOrGetConversation`
- `POST /messaging/get_backup.php` -> `handlers.GetBackup`

### Admin Domain
- `POST /admin/get_flagged_items.php` -> `handlers.GetFlaggedItems` (Admin Required)
- `POST /admin/resolve_report.php` -> `handlers.ResolveReport` (Admin Required)
- `POST /admin/take_down_listing.php` -> `handlers.TakeDownListing` (Admin Required)
- `POST /admin/ban_user.php` -> `handlers.BanUser` (Admin Required)
- `GET/POST /admin/get_notifications.php` -> `handlers.GetNotifications` (User facing)

## Shared Logic & Helpers
- **[social.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/pkg/utils/social.go)**: Ported WhatsApp, Facebook, and Instagram normalization logic.
- **[helpers.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/pkg/utils/helpers.go)**: Added `IsValidEmail` and `FormatAge` helpers.
- **[db.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/db/db.go)**: Expanded `PgxPool` interface to include `Begin` for transactions.

## Routing & Static Assets

- **Conflict Resolution**: Fixed a Gin routing panic caused by overlapping wildcard static routes and specific `.php` endpoints. I replaced the broad `r.Static` calls with a `NoRoute` fallback that serves static assets from `./listings`, `./profile_pics`, and `./downloads` only if they exist on disk and aren't caught by API handlers.
- **Support for Both Methods**: Standardized handlers like `GetListingDetails` and `GetMessages` to support both `GET` and `POST` as used by different parts of the Android client.

### Automated Tests
- Ran `go test ./...` in the `server/` directory.
- All existing tests passed, and the new code compiles correctly.

### Security Parity
- Verified all session checks, ownership verifications, and admin-only protections match the PHP implementation.
- All SQL queries use bound parameters to prevent injection.
