package com.example.exotrade.activities.listings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.messaging.ChatActivity
import com.example.exotrade.activities.profile.UserProfileBottomSheet
import com.example.exotrade.databinding.ListingActivityDetailsBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Detailed view of a specific animal listing.
 * Displays full specimen metadata, seller information, and social contact links.
 */
class ListingDetails : AppCompatActivity() {

    private lateinit var binding: ListingActivityDetailsBinding
    private var sellerId: String? = null
    private var sellerName: String? = null
    private var sellerPublicKey: String? = null
    private var listingName: String? = null
    private var currentListingId: String? = null
    private var currentImageUrl: String? = null
    private var currentStatus: String? = null
    private var sellerWhatsApp: String? = null
    private var sellerFacebook: String? = null
    private var sellerInstagram: String? = null
    private lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ListingActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupListeners()

        // 1. Try to get it as a String first
        var listingId: String? = intent.getStringExtra("listing_id")

        // 2. If it's null, try to extract it as an Int (handles the BrowseListingsFragment click)
        if (listingId == null && intent.hasExtra("listing_id")) {
            val intId = intent.getIntExtra("listing_id", -1)
            if (intId != -1) {
                listingId = intId.toString()
            }
        }

        // 3. Handle Deep Link
        if (listingId == null && intent.data != null) {
            val data: Uri? = intent.data
            listingId = data?.lastPathSegment
        }

        this.currentListingId = listingId
        listingId?.let { fetchListingDetails(it) }
    }

    private fun setupListeners() {
        binding.lblScientificName.setOnClickListener {
            sellerId?.let { id ->
                UserProfileBottomSheet.newInstance(id).show(supportFragmentManager, "user_profile")
            }
        }

        binding.lblCommonName.setOnClickListener {
            sellerId?.let { id ->
                UserProfileBottomSheet.newInstance(id).show(supportFragmentManager, "user_profile")
            }
        }

        binding.btnContactSeller.setOnClickListener {
            val id = sellerId ?: return@setOnClickListener
            val lid = currentListingId ?: return@setOnClickListener

            val params = session.authParams().toMutableMap()
            params["listing_id"] = lid
            params["seller_id"] = id

            lifecycleScope.launch {
                try {
                    val response: String = ExoTradeApplication.container.apiService.postForm("messaging/start_or_get_conversation", params)
                    val json = Json.parseToJsonElement(response).jsonObject
                    if ("success" == json["status"]?.jsonPrimitive?.contentOrNull) {
                        val details = json["listing_details"]?.jsonObject
                        val otherUser = json["other_user"]?.jsonObject

                        val intent = Intent(this@ListingDetails, ChatActivity::class.java).apply {
                            putExtra("conversation_id", json["conversation_id"]?.jsonPrimitive?.contentOrNull)

                            otherUser?.let {
                                putExtra("other_username", it["username"]?.jsonPrimitive?.contentOrNull)
                                putExtra("other_profile_pic", it["profile_pic"]?.jsonPrimitive?.contentOrNull)
                                putExtra("other_public_key", it["public_key"]?.jsonPrimitive?.contentOrNull)
                            }

                            details?.let {
                                putExtra("is_from_listing", true)
                                putExtra("listing_name", it["common_name"]?.jsonPrimitive?.contentOrNull ?: listingName)
                                putExtra("listing_id", it["id"]?.jsonPrimitive?.contentOrNull)
                                putExtra("listing_scientific", it["scientific_name"]?.jsonPrimitive?.contentOrNull)
                                putExtra("listing_price", it["price"]?.jsonPrimitive?.contentOrNull)
                                putExtra("listing_image", it["image_url"]?.jsonPrimitive?.contentOrNull)
                            }
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.contentOrNull, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ListingDetails, "Error starting conversation", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_listing_details, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_share) {
            ShareUtils.shareListingAsImage(
                this,
                currentListingId ?: "",
                binding.lblCommonName.text.toString(),
                binding.lblScientificName.text.toString(),
                binding.lblPrice.text.toString(),
                binding.lblDescription.text.toString(),
                currentImageUrl,
                "sold" == currentStatus,
                "sale",
                sellerWhatsApp,
                sellerFacebook,
                sellerInstagram
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchListingDetails(id: String) {
        val params = session.authParams().toMutableMap()
        params["id"] = id

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("listings/get_listing_details", params)
                Log.d("ListingDetails", "API Response: $response")

                val json = Json.parseToJsonElement(response).jsonObject

                // Check status from the root object
                if ("success" == json["status"]?.jsonPrimitive?.contentOrNull) {
                    withContext(Dispatchers.Main) {
                        displayDetails(json)
                    }
                } else {
                    val msg = json["message"]?.jsonPrimitive?.contentOrNull ?: "Failed to load listing"
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListingDetails, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ListingDetails", "Crash in parsing: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ListingDetails, "Error loading details", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayDetails(json: kotlinx.serialization.json.JsonObject) {
        // Force UI updates to the Main Thread to ensure they are visible
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                // Robust safe string extraction helper
                fun getString(key: String): String {
                    val element = json[key] ?: return ""
                    // Explicitly check for JsonNull to prevent crashes
                    if (element is kotlinx.serialization.json.JsonNull) return ""
                    return element.jsonPrimitive.contentOrNull ?: ""
                }

                // Apply UI updates safely
                val common = getString("common_name").ifEmpty { "Unknown" }
                listingName = common
                binding.collapsingToolbar.title = common
                binding.lblCommonName.text = common

                val scientific = getString("scientific_name").ifEmpty { getString("genus") + " " + getString("species") }.ifEmpty { "Unknown" }
                binding.lblScientificName.text = scientific

                val distribution = getString("distribution")
                binding.lblDistribution.text = if (distribution.isEmpty()) "Unknown" else distribution

                val status = getString("listing_status").ifEmpty { "active" }
                if ("sold" == status) {
                    binding.lblPrice.text = "SOLD"
                    binding.lblPrice.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                } else {
                    binding.lblPrice.text = getString("price_formatted").ifEmpty { "Contact" }
                }

                binding.lblSex.text = getString("sex").ifEmpty { "Unsexed" }

                val size = getString("size_in_cm")
                binding.lblSize.text = if (size.isEmpty()) "Unknown" else "$size cm"

                val age = getString("age")
                binding.lblAge.text = if (age.isEmpty()) "Unknown" else age

                val description = getString("description")
                binding.lblDescription.text = if (description.isEmpty()) "None" else description

                currentStatus = status
                currentImageUrl = getString("image_url")
                sellerId = getString("seller_id")
                sellerWhatsApp = getString("whatsapp")
                sellerFacebook = getString("facebook")
                sellerInstagram = getString("instagram")

                SocialLinkUtils.bindProfileIcons(
                    this@ListingDetails,
                    binding.layoutSocialSection,
                    binding.layoutSocialLinks,
                    binding.imgSocialWhatsApp,
                    binding.imgSocialFacebook,
                    binding.imgSocialInstagram,
                    sellerWhatsApp,
                    sellerFacebook,
                    sellerInstagram
                )

                // Update buttons
                val isOwner = sellerId?.equals(session.getUserUUID(), ignoreCase = true) == true
                if (isOwner) {
                    binding.btnContactSeller.visibility = View.GONE
                    if ("active" == status) {
                        binding.btnMarkSold.visibility = View.VISIBLE
                        binding.btnMarkSold.setOnClickListener { markAsSold(getString("id")) }
                    } else {
                        binding.btnMarkSold.visibility = View.GONE
                    }
                }

                if (session.isAdmin()) {
                    binding.btnTakeDown.visibility = View.VISIBLE
                    binding.btnTakeDown.setOnClickListener { showTakeDownDialog() }
                }

                val imagePath = currentImageUrl
                if (!imagePath.isNullOrEmpty() && "null" != imagePath) {
                    Helpers.loadImage(imagePath, binding.imgLargePreview)
                }
            } catch (e: Exception) {
                Log.e("ListingDetails", "UI Update crashed: ${e.message}", e)
                Toast.makeText(this@ListingDetails, "Error updating UI", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markAsSold(listingId: String) {
        val params = session.authParams().toMutableMap()
        params["listing_id"] = listingId
        params["status"] = "sold"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("listings/update_listing", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.contentOrNull) {
                    Toast.makeText(this@ListingDetails, "Marked as SOLD", Toast.LENGTH_SHORT).show()
                    fetchListingDetails(listingId)
                } else {
                    Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.contentOrNull, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ListingDetails, "Update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTakeDownDialog() {
        val etReason = EditText(this).apply {
            hint = "Enter reason for takedown..."
        }
        AlertDialog.Builder(this)
            .setTitle("Take Down Listing")
            .setView(etReason)
            .setPositiveButton("Take Down") { _, _ ->
                var reason = etReason.text.toString().trim()
                if (reason.isEmpty()) reason = "Violation of community guidelines"
                currentListingId?.let { takeDownListing(it, reason) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun takeDownListing(listingId: String, reason: String) {
        val params = session.authParams().toMutableMap()
        params["listing_id"] = listingId
        params["reason"] = reason
        params["kind"] = "sale"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("admin/take_down_listing", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.contentOrNull) {
                    Toast.makeText(this@ListingDetails, "Listing taken down", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ListingDetails, json["message"]?.jsonPrimitive?.contentOrNull, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ListingDetails, "Takedown failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}