package com.example.exotrade.activities.listings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.exotrade.Adapters.ListingAdapter
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.breeding.BreedingFeed
import com.example.exotrade.activities.profile.Profile
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.databinding.ListingActivityBrowseBinding
import com.example.exotrade.models.Listing
import com.example.exotrade.utils.*
import com.example.exotrade.viewmodels.BrowseListingsViewModel
import com.example.exotrade.viewmodels.ViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main marketplace feed fragment.
 * Displays a searchable, filterable, and paginated list of animal listings.
 */
class BrowseListingsFragment : Fragment() {
    private var _binding: ListingActivityBrowseBinding? = null
    private val binding get() = _binding!!
    
    private val session: SessionRepository by lazy { ExoTradeApplication.container.sessionRepository }
    private lateinit var adapter: ListingAdapter
    private lateinit var layoutManager: LinearLayoutManager
    
    private val viewModel: BrowseListingsViewModel by viewModels { ViewModelFactory() }
    
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ListingActivityBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            ExoTradeApplication.container.speciesRepository.preloadCache()
        }

        setupUI()
        observeViewModel()
        viewModel.refresh()
    }

    private fun setupUI() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(400)
                    viewModel.setSearchQuery(query)
                }
            }
        })

        binding.toggleFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && checkedId == R.id.btnFilterBreeding) {
                startActivity(Intent(requireContext(), BreedingFeed::class.java))
            }
        }

        layoutManager = LinearLayoutManager(requireContext())
        binding.rvListings.layoutManager = layoutManager

        adapter = ListingAdapter(ArrayList(), object : ListingAdapter.OnListingListener {
            override fun onListingClick(listing: Listing) {
                val intent = Intent(requireContext(), ListingDetails::class.java)
                intent.putExtra("listing_id", listing.id)
                startActivity(intent)
            }

            override fun onListingLongClick(listing: Listing, view: View, x: Float, y: Float) {
                showListingMenu(listing, view)
            }
        })
        binding.rvListings.adapter = adapter

        binding.rvListings.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.listings.collect { listings ->
                        adapter.setListings(listings)
                        binding.emptyState.visibility = if (listings.isEmpty() && !viewModel.isLoading.value) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        // Show/hide loading indicator if needed
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { isRefreshing ->
                        binding.swipeRefresh.isRefreshing = isRefreshing
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Helpers.checkAdminNotifications(requireActivity())
    }

    private fun showListingMenu(listing: Listing, view: View) {
        val popup = PopupMenu(requireContext(), view)
        val isOwner = session.getUserUUID() != null && session.getUserUUID()?.equals(listing.sellerId, ignoreCase = true) == true

        if (isOwner) {
            popup.menu.add("Edit")
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
                "Edit" -> {
                    val intent = Intent(requireContext(), EditListing::class.java)
                    intent.putExtra("listing_id", listing.id)
                    startActivity(intent)
                }
                "WhatsApp Seller" -> SocialLinkUtils.openWhatsApp(requireActivity(), listing.whatsapp)
                "View Seller Profile" -> {
                    val intent = Intent(requireContext(), Profile::class.java)
                    intent.putExtra("user_id", listing.sellerId)
                    startActivity(intent)
                }
                "Share" -> ShareUtils.shareListingAsImage(
                    requireActivity(), listing.id.toString(), listing.commonName, listing.scientificName,
                    listing.price, listing.description, listing.imageUrl,
                    "sold" == listing.status, "sale", listing.whatsapp,
                    listing.facebook, listing.instagram
                )
                "Report" -> ReportDialog.show(requireContext(), "listing", listing.id.toString(), null)
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
                        if (response.contains("\"status\":\"success\"")) {
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                            viewModel.refresh()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error deleting listing", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
