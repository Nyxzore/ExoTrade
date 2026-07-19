package com.example.exotrade.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Listing(
    val id: Int,
    @SerialName("common_name") val commonName: String? = null,
    @SerialName("scientific_name") val scientificName: String? = null,
    val price: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("seller_id") val sellerId: String? = null,
    @SerialName("listing_type") val listingType: String? = null,
    val sex: String? = null,
    val status: String? = null,
    @SerialName("subscription_tier") val subscriptionTier: Int = 0,
    val whatsapp: String? = null,
    val facebook: String? = null,
    val instagram: String? = null,
    @SerialName("listed_time") val listedTime: String? = null,
    val probability: Double = 0.0
)