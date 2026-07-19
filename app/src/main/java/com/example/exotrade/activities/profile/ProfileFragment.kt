package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int

/**
 * Fragment for displaying the current user's own profile.
 */
class ProfileFragment : Fragment() {
    private var _binding: ProfileActivityMainBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var session: SessionRepository
    private val allFetchedListings = mutableListOf<Listing>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProfileActivityMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        session = ExoTradeApplication.container.sessionRepository

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.rvMyListings.layoutManager = LinearLayoutManager(requireContext())

        // Hardcode for self-view
        binding.btnEditProfile.visibility = View.VISIBLE
        binding.btnSettings.visibility = View.VISIBLE
        binding.btnAddFriend.visibility = View.GONE
        binding.btnFriends.visibility = View.VISIBLE
        binding.btnReportUser.visibility = View.GONE
        
        binding.lblMyListings.text = "My Listings"
    }

    private fun setupListeners() {
        binding.switchShowSold.setOnCheckedChangeListener { _, _ ->
            filterAndDisplayListings()
        }

        binding.btnFriends.setOnClickListener {
            startActivity(Intent(requireContext(), FriendsActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditAccount::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            fetchProfileData()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            fetchProfileData()
        }
    }

    private fun fetchProfileData() {
        val targetId = session.getUserUUID() ?: return
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = targetId

        viewLifecycleOwner.lifecycleScope.launch {
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

                    session.updateUserInfo(
                        username = username,
                        profilePic = picPath ?: "",
                        tier = tier,
                        isAdmin = json["is_admin"]?.jsonPrimitive?.boolean ?: false
                    )

                    val whatsapp = json["whatsapp"]?.jsonPrimitive?.content
                    val facebook = json["facebook"]?.jsonPrimitive?.content
                    val instagram = json["instagram"]?.jsonPrimitive?.content

                    SocialLinkUtils.bindProfileIcons(
                        requireActivity(),
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
                            imgProfile.strokeWidth = Helpers.dpToPx(requireContext(), 1).toFloat()
                            imgProfile.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.tier_1_orange)
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
                                sellerId = targetId,
                                listingType = l["kind"]?.jsonPrimitive?.content,
                                sex = l["sex"]?.jsonPrimitive?.content,
                                status = l["status"]?.jsonPrimitive?.content,
                                subscriptionTier = l["subscription_tier"]?.jsonPrimitive?.int ?: 0
                            )
                        )
                    }
                    filterAndDisplayListings()
                } else {
                    val msg = json["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    Toast.makeText(requireContext(), "Error: $msg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndDisplayListings() {
        val showSold = binding.switchShowSold.isChecked
        val filtered = allFetchedListings.filter { showSold || "sold" != it.status }

        val adapter = ListingAdapter(filtered.toMutableList(), object : ListingAdapter.OnListingListener {
            override fun onListingClick(listing: Listing) {
                val intent = if ("breeding" == listing.listingType) {
                    Intent(requireContext(), com.example.exotrade.activities.breeding.BreedingListingDetails::class.java)
                } else {
                    Intent(requireContext(), ListingDetails::class.java)
                }
                intent.putExtra("listing_id", listing.id)
                startActivity(intent)
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {
                showListingMenu(listing, view)
            }
        })
        binding.rvMyListings.adapter = adapter
    }

    private fun showListingMenu(listing: Listing, view: View) {
        val popup = PopupMenu(requireContext(), view)
        
        popup.menu.add("Edit")
        popup.menu.add("Delete")
        popup.menu.add("Share")

        popup.setOnMenuItemClickListener { item ->
            when (item.title?.toString()) {
                "Delete" -> deleteListing(listing.id.toString())
                "Edit" -> {
                    val intent = Intent(requireContext(), EditListing::class.java)
                    intent.putExtra("listing_id", listing.id)
                    startActivity(intent)
                }
                "Share" -> com.example.exotrade.utils.ShareUtils.shareListingAsImage(
                    requireActivity(),
                    listing.id.toString(),
                    listing.commonName,
                    listing.scientificName,
                    listing.price,
                    listing.description,
                    listing.imageUrl,
                    "sold" == listing.status,
                    listing.listingType,
                    listing.whatsapp,
                    listing.facebook,
                    listing.instagram
                )
            }
            true
        }
        popup.show()
    }

    private fun deleteListing(listingId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to delete this listing?")
            .setPositiveButton("Yes") { _, _ ->
                val params = session.authParams().toMutableMap()
                params["listing_id"] = listingId

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val response: String = ExoTradeApplication.container.apiService.postForm("listings/delete_listing", params)
                        val json = Json.parseToJsonElement(response).jsonObject
                        if ("success" == json["status"]?.jsonPrimitive?.content) {
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                            fetchProfileData()
                        } else {
                            Toast.makeText(requireContext(), json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error deleting", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
