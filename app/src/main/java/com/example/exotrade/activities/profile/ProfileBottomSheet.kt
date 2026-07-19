package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.models.Listing
import com.example.exotrade.R
import com.example.exotrade.activities.listings.ListingDetails
import com.example.exotrade.databinding.LayoutProfileBottomSheetBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.Helpers
import com.example.exotrade.utils.SocialLinkUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * A bottom sheet dialog fragment that displays a user's profile preview.
 */
class ProfileBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutProfileBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionRepository
    private var userId: String? = null
    private var isSelf: Boolean = false
    private val allFetchedListings = mutableListOf<Listing>()

    companion object {
        private const val ARG_USER_ID = "user_id"

        fun newInstance(userId: String): ProfileBottomSheet {
            val fragment = ProfileBottomSheet()
            val args = Bundle()
            args.putString(ARG_USER_ID, userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString(ARG_USER_ID)
        session = ExoTradeApplication.container.sessionRepository
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutProfileBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvMyListings.layoutManager = LinearLayoutManager(requireContext())

        binding.switchShowSold.setOnCheckedChangeListener { _, _ -> filterAndDisplayListings() }

        isSelf = userId == session.getUserUUID()
        binding.btnEditProfile.visibility = if (isSelf) View.VISIBLE else View.GONE
        binding.btnAddFriend.visibility = if (isSelf) View.GONE else View.VISIBLE
        binding.btnReportUser.visibility = if (isSelf) View.GONE else View.VISIBLE

        if (isSelf) {
            binding.lblMyListings.text = "My Listings"
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditAccount::class.java))
            dismiss()
        }

        if (!isSelf) {
            binding.btnAddFriend.setOnClickListener { addFriend() }
        }
        binding.btnReportUser.setOnClickListener {
            com.example.exotrade.utils.ReportDialog.show(requireActivity(), "user", userId ?: "", null)
        }

        fetchProfileData()
    }

    private fun fetchProfileData() {
        val targetId = userId ?: return
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = targetId

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("profile/get_profile", params)
                if (!isAdded) return@launch
                
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

                    val whatsapp = json["whatsapp"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
                    val facebook = json["facebook"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
                    val instagram = json["instagram"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content

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
                                sellerId = userId,
                                listingType = l["kind"]?.jsonPrimitive?.content,
                                sex = l["sex"]?.jsonPrimitive?.content,
                                status = l["status"]?.jsonPrimitive?.content,
                                subscriptionTier = l["subscription_tier"]?.jsonPrimitive?.int ?: 0,
                                whatsapp = whatsapp,
                                facebook = facebook,
                                instagram = instagram
                            )
                        )
                    }
                    filterAndDisplayListings()
                    updateFriendshipButton(json["friendship_status"]?.jsonPrimitive?.content ?: "none")
                }
            } catch (e: Exception) {}
        }
    }

    private fun updateFriendshipButton(status: String) {
        if (!isAdded || isSelf) return
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
                binding.btnAddFriend.setOnClickListener { respondToFriendRequest() }
            }
            else -> {
                binding.btnAddFriend.setText(R.string.add_friend)
                binding.btnAddFriend.setOnClickListener { addFriend() }
            }
        }
    }

    private fun respondToFriendRequest() {
        val targetId = userId ?: return
        val params = session.authParams().toMutableMap()
        params["requester_id"] = targetId

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/accept_friend_request", params)
                if (!isAdded) return@launch
                val json = Json.parseToJsonElement(response).jsonObject
                Toast.makeText(requireContext(), json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    updateFriendshipButton("friends")
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Action failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndDisplayListings() {
        if (!isAdded) return
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
                dismiss()
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {}
        })
        binding.rvMyListings.adapter = adapter
    }

    private fun addFriend() {
        val targetId = userId ?: return
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = targetId

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("friends/send_friend_request", params)
                if (!isAdded) return@launch
                val json = Json.parseToJsonElement(response).jsonObject
                Toast.makeText(requireContext(), json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    updateFriendshipButton("pending_sent")
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to send friend request", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
