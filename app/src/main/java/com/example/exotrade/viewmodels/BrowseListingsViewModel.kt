package com.example.exotrade.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exotrade.data.ApiService
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.models.Listing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.decodeFromJsonElement

class BrowseListingsViewModel(
    private val apiService: ApiService,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings: StateFlow<List<Listing>> = _listings

    val isLoading = MutableStateFlow(false)
    val isRefreshing = MutableStateFlow(false)

    private var currentOffset = 0
    private var currentQuery = ""
    private val json = Json { ignoreUnknownKeys = true }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            currentOffset = 0
            fetchListings(true)
            isRefreshing.value = false
        }
    }

    fun loadNextPage() {
        if (isLoading.value) return
        viewModelScope.launch {
            isLoading.value = true
            fetchListings(false)
            isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        currentQuery = query
        refresh()
    }

    private suspend fun fetchListings(overwrite: Boolean) {
        try {
            Log.d("BrowseListings", "Attempting to fetch listings. Offset: $currentOffset")

            val params = sessionRepository.authParams().toMutableMap()
            params["offset"] = currentOffset.toString()
            if (currentQuery.isNotEmpty()) {
                params["search"] = currentQuery
            }

            Log.d("BrowseListings", "Params built successfully, calling API...")

            val response: String = apiService.postForm("listings/get_all_listings", params)
            Log.d("BrowseListings", "API Response received: $response")

            val root = Json.parseToJsonElement(response).jsonObject
            if (root["status"]?.toString()?.contains("success") == true) {

                // Falls back to checking inside a "data" object if the root array is null
                val data = root["listings"]?.jsonArray ?: root["data"]?.jsonObject?.get("listings")?.jsonArray

                if (data == null) {
                    Log.e("BrowseListings", "Listings array was null in the JSON response")
                    return
                }

                val newListings = data.map { json.decodeFromJsonElement<Listing>(it) }

                if (overwrite) {
                    _listings.value = newListings
                } else {
                    _listings.value +=  newListings
                }
                currentOffset += newListings.size
                Log.d("BrowseListings", "Successfully loaded ${newListings.size} listings")
            } else {
                Log.e("BrowseListings", "API returned non-success status: $response")
            }
        } catch (e: Exception) {
            Log.e("BrowseListings", "Failed to fetch listings: ${e.message}", e)
        }
    }
}