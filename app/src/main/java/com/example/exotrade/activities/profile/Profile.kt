package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.activities.listings.EditListing
import com.example.exotrade.activities.listings.ListingDetails
import com.example.exotrade.databinding.ProfileActivityMainBinding
import com.example.exotrade.models.Listing
import com.example.exotrade.utils.Helpers
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.SocialLinkUtils
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * Activity for displaying another user's profile, including their bio, social links, and listings.
 * This activity is used when viewing profiles other than the current user's.
 */
class Profile : BaseActivity() {
    private lateinit var binding: ProfileActivityMainBinding
    private lateinit var session: SessionRepository
    private var viewUserId: String? = null
    private val allFetchedListings = mutableListOf<Listing>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        viewUserId = intent.getStringExtra("user_id")

        if (viewUserId == null || viewUserId == session.getUserUUID()) {
            // This activity should only be used for other users.
            // If it's the current user, or ID is missing, we shouldn't be here.
            finish()
            return
        }

        setupUI()
        setupListeners()
        setupNavigation()
    }

    private fun setupUI() {
        binding.rvMyListings.layoutManager = LinearLayoutManager(this)

        binding.btnEditProfile.visibility = View.GONE
        binding.btnSettings.visibility = View.GONE
        binding.btnAddFriend.visibility = View.VISIBLE
        binding.btnFriends.visibility = View.GONE
        binding.btnReportUser.visibility = View.VISIBLE

        binding.lblMyListings.text = "Listings"
    }

    private fun setupListeners() {
        binding.switchShowSold.setOnCheckedChangeListener { _, _ ->
            filterAndDisplayListings()
        }

        binding.btnAddFriend.setOnClickListener { addFriend() }
        binding.btnReportUser.setOnClickListener {
            com.example.exotrade.utils.ReportDialog.show(this, "user", viewUserId ?: "", null)
        }
    }

    private fun setupNavigation() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        fetchProfileData()
    }

    private fun fetchProfileData() {
        val targetId = viewUserId ?: return
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = targetId

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("profile/get_profile", params)
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val username = json["username"]?.jsonPrimitive?.content ?: ""
                    binding.lblUsername.text = username

                    val picPath = json["profile_picture"]?.jsonPrimitive?.content
                    Helpers.loadImage(picPath, binding.imgProfilePicture, R.drawable.ic_person_24)

                    val tierElement = json["subscription_tier"]
                    val tier = if (tierElement != null && tierElement !is kotlinx.serialization.json.JsonNull) {
                        tierElement.jsonPrimitive.int
                    } else 0

                    val whatsapp = json["whatsapp"]?.jsonPrimitive?.content
                    val facebook = json["facebook"]?.jsonPrimitive?.content
                    val instagram = json["instagram"]?.jsonPrimitive?.content

                    SocialLinkUtils.bindProfileIcons(
                        this@Profile,
                        binding.layoutSocialLinks,
                        binding.imgSocialWhatsApp,
                        binding.imgSocialFacebook,
                        binding.imgSocialInstagram,
                        whatsapp,
                        facebook,
                        instagram
                    )

                    val imgProfile = binding.imgProfilePicture
                    if (imgProfile is ShapeableImageView) {
                        if (tier >= 1) {
                            imgProfile.strokeWidth = Helpers.dpToPx(this@Profile, 1).toFloat()
                            imgProfile.strokeColor = ContextCompat.getColorStateList(this@Profile, R.color.tier_1_orange)
                        } else {
                            imgProfile.strokeWidth = 0f
                        }
                    }

                    val listingsArray = json["listings"]?.jsonArray
                    allFetchedListings.clear()
                    listingsArray?.forEach { element ->
                        val l = element.jsonObject
                        allFetchedListings.add(
                            Listing(
                                id = l["id"]?.jsonPrimitive?.int ?: 0,
                                commonName = l["common_name"]?.jsonPrimitive?.content,
                                scientificName = l["scientific_name"]?.jsonPrimitive?.content,
                                price = l["price"]?.jsonPrimitive?.content,
                                description = l["description"]?.jsonPrimitive?.content,
                                imageUrl = l["image_url"]?.jsonPrimitive?.content,
                                sellerId = viewUserId,
                                listingType = l["kind"]?.jsonPrimitive?.content,
                                sex = l["sex"]?.jsonPrimitive?.content,
                                status = l["status"]?.jsonPrimitive?.content,
                                subscriptionTier = l["subscription_tier"]?.jsonPrimitive?.int ?: 0
                            )
                        )
                    }
                    filterAndDisplayListings()
                    updateFriendshipButton(json["friendship_status"]?.jsonPrimitive?.content ?: "none")
                } else {
                    val msg = json["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    Toast.makeText(this@Profile, "Error: $msg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Profile, "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndDisplayListings() {
        val showSold = binding.switchShowSold.isChecked
        val filtered = allFetchedListings.filter { showSold || "sold" != it.status }

        val adapter = ListingAdapter(filtered.toMutableList(), object : ListingAdapter.OnListingListener {
            override fun onListingClick(listing: Listing) {
                val intent = if ("breeding" == listing.listingType) {
                    Intent(this@Profile, com.example.exotrade.activities.breeding.BreedingListingDetails::class.java)
                } else {
                    Intent(this@Profile, ListingDetails::class.java)
                }
                intent.putExtra("listing_id", listing.id)
                startActivity(intent)
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {
                // Other users' profiles don't have listing management menus
            }
        })
        binding.rvMyListings.adapter = adapter
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun updateFriendshipButton(status: String) {
        binding.btnAddFriend.isEnabled = true
        binding.btnAddFriend.visibility = View.VISIBLE
        when (status) {
            "friends" -> {
                binding.btnAddFriend.text = getString(R.string.friends)
                binding.btnAddFriend.isEnabled = false
                binding.btnAddFriend.setOnClickListener(null)
            }
            "pending_sent" -> {
                binding.btnAddFriend.text = "Requested"
                binding.btnAddFriend.isEnabled = false
                binding.btnAddFriend.setOnClickListener(null)
            }
            "pending_received" -> {
                binding.btnAddFriend.setText(R.string.accept)
                binding.btnAddFriend.setOnClickListener { respondToFriendRequest(true) }
            }
            else -> {
                binding.btnAddFriend.setText(R.string.add_friend)
                binding.btnAddFriend.setOnClickListener { addFriend() }
            }
        }
    }

    private fun respondToFriendRequest(accept: Boolean) {
        val params = session.authParams().toMutableMap()
        params["requester_id"] = viewUserId ?: ""
        val endpoint = if (accept) "friends/accept_friend_request" else "friends/decline_friend_request"

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm(endpoint, params)
                val json = Json.parseToJsonElement(response).jsonObject
                Toast.makeText(this@Profile, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    fetchProfileData()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Profile, "Action failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addFriend() {
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = viewUserId ?: ""

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/send_friend_request", params)
                val json = Json.parseToJsonElement(response).jsonObject
                Toast.makeText(this@Profile, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    updateFriendshipButton("pending_sent")
                }
            } catch (e: Exception) {
                Toast.makeText(this@Profile, "Failed to send friend request", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
