package com.example.exotrade.activities.breeding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.models.Listing
import com.example.exotrade.R
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.databinding.BreedingActivityFeedBinding
import com.example.exotrade.utils.Helpers
import com.example.exotrade.utils.NavigationHelper
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.utils.ShareUtils
import com.example.exotrade.utils.SocialLinkUtils
import com.example.exotrade.utils.ReportDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * Specialized feed for breeding listings.
 */
class BreedingFeed : AppCompatActivity() {
    private lateinit var binding: BreedingActivityFeedBinding
    private lateinit var session: SessionRepository
    private lateinit var adapter: ListingAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var isLoading = false
    private var hasReachedEnd = false
    private var currentOffset = 0
    private var currentSeed = System.currentTimeMillis().toString()
    private var searchQuery = ""
    private var currentBreedingType: String? = null
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BreedingActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository

        binding.swipeRefresh.setOnRefreshListener {
            refreshListings()
        }

        val matchListingId = intent.getStringExtra("match_listing_id")
        if (matchListingId != null) {
            binding.searchLayout.visibility = View.GONE
            binding.toggleFilter.visibility = View.GONE
            binding.scrollToggle.visibility = View.GONE
            setTitle(R.string.breeding_matches)
        }

        binding.etSearch.addTextChangedListener { s ->
            searchQuery = s?.toString()?.trim() ?: ""
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(400)
                refreshListings()
            }
        }

        binding.toggleBreedingType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentBreedingType = when (checkedId) {
                    R.id.btnAll -> null
                    R.id.btnSeeking -> "seeking"
                    R.id.btnLoan -> "loan"
                    else -> currentBreedingType
                }
                refreshListings()
            }
        }

        binding.toggleFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && checkedId == R.id.btnFilterSale) {
                startActivity(Intent(this, MainHostActivity::class.java))
                finish()
            }
        }

        NavigationHelper.setup(this, binding.bottomNavigation, R.id.nav_home)

        layoutManager = LinearLayoutManager(this)
        binding.rvBreeding.layoutManager = layoutManager

        adapter = ListingAdapter(mutableListOf(), object : ListingAdapter.OnListingListener {
            override fun onListingClick(listing: Listing) {
                val intent = Intent(this@BreedingFeed, BreedingListingDetails::class.java)
                intent.putExtra("listing_id", listing.id)
                startActivity(intent)
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {
                showMenu(listing, view)
            }
        })
        binding.rvBreeding.adapter = adapter

        binding.rvBreeding.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isLoading && !hasReachedEnd && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2) {
                    fetchBreedingListings()
                }
            }
        })

        fetchBreedingListings()
    }

    private fun refreshListings() {
        currentOffset = 0
        currentSeed = System.currentTimeMillis().toString()
        hasReachedEnd = false
        adapter.clear()
        fetchBreedingListings()
    }

    private fun showMenu(listing: Listing, view: View) {
        val popup = PopupMenu(this, view)
        val isOwner = session.getUserUUID() != null && session.getUserUUID() == listing.sellerId

        if (isOwner) {
            popup.menu.add("Delete")
        } else {
            if (!SocialLinkUtils.isBlank(listing.whatsapp)) {
                popup.menu.add("WhatsApp Seller")
            }
            popup.menu.add("View Seller Profile")
        }
        popup.menu.add("Share")
        if (!isOwner) {
            popup.menu.add("Report")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Delete" -> deleteListing(listing.id.toString())
                "WhatsApp Seller" -> SocialLinkUtils.openWhatsApp(this, listing.whatsapp)
                "View Seller Profile" -> {
                    val intent = Intent(this, com.example.exotrade.activities.profile.Profile::class.java)
                    intent.putExtra("user_id", listing.sellerId)
                    startActivity(intent)
                }
                "Share" -> ShareUtils.shareListingAsImage(
                    this,
                    listing.id.toString(),
                    listing.commonName,
                    listing.scientificName,
                    listing.price,
                    listing.description,
                    listing.imageUrl,
                    false,
                    "breeding",
                    listing.whatsapp,
                    listing.facebook,
                    listing.instagram
                )
                "Report" -> ReportDialog.show(this, "breeding", listing.id.toString(), null)
            }
            true
        }
        popup.show()
    }

    private fun deleteListing(id: String) {
        val params = session.authParams().toMutableMap()
        params["id"] = id
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("breeding/delete_breeding_listing", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    Toast.makeText(this@BreedingFeed, "Deleted", Toast.LENGTH_SHORT).show()
                    refreshListings()
                }
            } catch (e: Exception) {}
        }
    }

    private fun fetchBreedingListings() {
        isLoading = true
        val params = session.authParams().toMutableMap()
        
        val matchListingId = intent.getStringExtra("match_listing_id")
        val endpoint = if (matchListingId != null) {
            params["listing_id"] = matchListingId
            "breeding/find_breeding_matches"
        } else {
            params["offset"] = currentOffset.toString()
            params["seed"] = currentSeed
            if (searchQuery.isNotEmpty()) params["search"] = searchQuery
            currentBreedingType?.let { params["breeding_type"] = it }
            "breeding/get_breeding_listings"
        }

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm(endpoint, params)
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
                
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    val arr = json["listings"]?.jsonArray

                    if ((arr == null || arr.isEmpty()) && currentOffset == 0) {
                        binding.emptyState.visibility = View.VISIBLE
                        hasReachedEnd = true
                        return@launch
                    } else {
                        binding.emptyState.visibility = View.GONE
                    }

                    if (arr == null || arr.isEmpty()) {
                        hasReachedEnd = true
                        return@launch
                    }
                    
                    val list = mutableListOf<Listing>()
                    arr.forEach { element ->
                        val l = element.jsonObject
                        list.add(Listing(
                            id = l["id"]?.jsonPrimitive?.int ?: 0,
                            commonName = l["common_name"]?.jsonPrimitive?.content,
                            scientificName = l["scientific_name"]?.jsonPrimitive?.content,
                            price = l["price"]?.jsonPrimitive?.content, 
                            description = l["description"]?.jsonPrimitive?.content,
                            imageUrl = l["image_url"]?.jsonPrimitive?.content,
                            sellerId = l["seller_id"]?.jsonPrimitive?.content,
                            listingType = "breeding",
                            sex = l["sex"]?.jsonPrimitive?.content,
                            status = l["status"]?.jsonPrimitive?.content,
                            subscriptionTier = l["subscription_tier"]?.jsonPrimitive?.int ?: 0,
                            whatsapp = l["whatsapp"]?.jsonPrimitive?.content,
                            facebook = l["facebook"]?.jsonPrimitive?.content,
                            instagram = l["instagram"]?.jsonPrimitive?.content,
                            listedTime = l["listed_time"]?.jsonPrimitive?.content
                        ))
                    }
                    currentOffset += list.size
                    adapter.addListings(list)
                    if (list.size < 10) hasReachedEnd = true
                }
            } catch (e: Exception) {
                isLoading = false
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Helpers.updateUnreadBadge(binding.bottomNavigation)
        Helpers.checkAdminNotifications(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
