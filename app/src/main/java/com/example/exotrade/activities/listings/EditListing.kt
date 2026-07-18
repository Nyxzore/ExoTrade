package com.example.exotrade.activities.listings

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.databinding.ListingActivityCreateBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.data.SpeciesRepository
import com.example.exotrade.utils.*
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity for modifying an existing animal listing.
 * Pre-populates fields with current listing data and allows updating metadata or photos.
 */
class EditListing : BaseActivity() {

    private lateinit var binding: ListingActivityCreateBinding
    private lateinit var session: SessionRepository
    private lateinit var speciesRepository: SpeciesRepository
    private var listingId: String? = null
    private var selectedImageUri: Uri? = null
    private var currentListingType = "sale"
    private var isSyncing = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            binding.imgPreview.setImageURI(it)
            selectedImageUri = it
            binding.imgPreview.alpha = 1.0f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ListingActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        speciesRepository = ExoTradeApplication.container.speciesRepository
        listingId = intent.getStringExtra("listing_id")

        setupUI(savedInstanceState)
        loadSpeciesData()
        
        listingId?.let { loadListingData(it) }
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        binding.btnCreateListing.text = "Save Changes"
        binding.btnAddImage.text = "Change Photo"
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Edit Listing"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.toggleListingType.visibility = View.GONE

        binding.btnAddImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnCreateListing.setOnClickListener { updateListing() }

        binding.etSex.setSimpleItems(arrayOf("Male", "Female", "Unsexed"))
        binding.etAgeUnit.setSimpleItems(arrayOf(getString(R.string.days), getString(R.string.months), getString(R.string.years)))

        if (savedInstanceState == null) {
            binding.etSex.setText("Unsexed", false)
            binding.etAgeUnit.setText(getString(R.string.months), false)
        } else {
            binding.etSex.setText(savedInstanceState.getString("sex_value", "Unsexed"), false)
            binding.etAgeUnit.setText(savedInstanceState.getString("age_unit_value", getString(R.string.months)), false)
        }
    }

    private fun loadSpeciesData() {
        lifecycleScope.launch {
            val scientificList = speciesRepository.getNames(isScientific = true)
            val commonList = speciesRepository.getNames(isScientific = false)

            if (scientificList.isEmpty()) {
                if (isSyncing) return@launch
                isSyncing = true
                speciesRepository.syncFromServer(true)
                isSyncing = false
                loadSpeciesData()
                return@launch
            }

            val sAdapter = ArrayAdapter(this@EditListing, android.R.layout.simple_dropdown_item_1line, scientificList)
            binding.etScientificName.setAdapter(sAdapter)
            binding.etScientificName.threshold = 1

            val cAdapter = ArrayAdapter(this@EditListing, android.R.layout.simple_dropdown_item_1line, commonList)
            binding.etCommonName.setAdapter(cAdapter)
            binding.etCommonName.threshold = 1

            binding.etCommonName.setOnItemClickListener { parent, _, position, _ ->
                val selectedCommon = parent.getItemAtPosition(position) as String
                lifecycleScope.launch {
                    val scientific = speciesRepository.getScientificName(selectedCommon)
                    if (!scientific.isNullOrEmpty()) {
                        binding.etScientificName.setText(scientific, false)
                    }
                }
            }

            binding.etScientificName.setOnItemClickListener { parent, _, position, _ ->
                val selectedScientific = parent.getItemAtPosition(position) as String
                lifecycleScope.launch {
                    val common = speciesRepository.getCommonName(selectedScientific)
                    if (!common.isNullOrEmpty()) {
                        binding.etCommonName.setText(common, false)
                    } else {
                        binding.etCommonName.setText("None (Uses Scientific Name)", false)
                    }
                }
            }
        }
    }

    private fun loadListingData(id: String) {
        binding.progressBar.visibility = View.VISIBLE
        val params = session.authParams().toMutableMap()
        params["id"] = id

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("listings/get_listing_details", params)
                binding.progressBar.visibility = View.GONE
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    binding.etCommonName.setText(json["common_name"]?.jsonPrimitive?.content ?: "", false)
                    binding.etScientificName.setText(json["scientific_name"]?.jsonPrimitive?.content ?: "", false)
                    binding.etPrice.setText(json["price_raw"]?.jsonPrimitive?.content ?: "")
                    binding.etDescription.setText(json["description"]?.jsonPrimitive?.content ?: "")
                    binding.etSize.setText(json["size_in_cm"]?.jsonPrimitive?.content ?: "")

                    val rawDaysStr = json["age_raw"]?.jsonPrimitive?.content ?: ""
                    if (rawDaysStr.isNotEmpty()) {
                        try {
                            val days = rawDaysStr.toInt()
                            when {
                                days % 365 == 0 -> {
                                    binding.etAge.setText((days / 365).toString())
                                    binding.etAgeUnit.setText(getString(R.string.years), false)
                                }
                                days % 30 == 0 -> {
                                    binding.etAge.setText((days / 30).toString())
                                    binding.etAgeUnit.setText(getString(R.string.months), false)
                                }
                                else -> {
                                    binding.etAge.setText(days.toString())
                                    binding.etAgeUnit.setText(getString(R.string.days), false)
                                }
                            }
                        } catch (e: NumberFormatException) {}
                    }

                    binding.etSex.setText(json["sex"]?.jsonPrimitive?.content ?: "Unsexed", false)

                    val tilPrice = binding.etPrice.parent.parent as TextInputLayout
                    tilPrice.hint = getString(R.string.asking_price)

                    val imgUrl = json["image_url"]?.jsonPrimitive?.content ?: ""
                    if (imgUrl.isNotEmpty() && "null" != imgUrl) {
                        Helpers.loadImage(imgUrl, binding.imgPreview)
                        binding.imgPreview.alpha = 1.0f
                    }
                } else {
                    Toast.makeText(this@EditListing, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@EditListing, "Error loading data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("sex_value", binding.etSex.text.toString())
        outState.putString("age_unit_value", binding.etAgeUnit.text.toString())
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private suspend fun encodeImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ImageUtils.compressAndEncode(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateListing() {
        val scientificName = binding.etScientificName.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sex = binding.etSex.text.toString().trim()
        val size = binding.etSize.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val unit = binding.etAgeUnit.text.toString().trim()

        if (scientificName.isEmpty()) {
            Toast.makeText(this, "Scientific name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentListingType == "sale" && price.isEmpty()) {
            Toast.makeText(this, "Price is required", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (!speciesRepository.isValidSpecies(scientificName)) {
                Toast.makeText(this@EditListing, "Please select a valid species from the list", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val lsid = speciesRepository.getLsid(scientificName)
            val params = session.authParams().toMutableMap()
            params["listing_id"] = listingId ?: ""
            params["price"] = price
            params["description"] = description
            params["species_lsid"] = lsid ?: ""
            params["sex"] = sex
            params["size_in_cm"] = size
            
            if (ageStr.isNotEmpty()) {
                try {
                    val ageVal = ageStr.toInt()
                    var days = 0
                    when (unit) {
                        getString(R.string.days) -> days = ageVal
                        getString(R.string.months) -> days = ageVal * 30
                        getString(R.string.years) -> days = ageVal * 365
                    }
                    params["age_in_days"] = days.toString()
                } catch (e: NumberFormatException) {}
            }

            selectedImageUri?.let {
                binding.btnCreateListing.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                val imageData = encodeImage(it)
                if (imageData != null) {
                    params["image_data"] = imageData
                }
            } ?: run {
                binding.btnCreateListing.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
            }

            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("listings/update_listing", params)
                binding.btnCreateListing.isEnabled = true
                binding.progressBar.visibility = View.GONE
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    Toast.makeText(this@EditListing, "Listing updated!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@EditListing, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.btnCreateListing.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@EditListing, "Failed to connect to server", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
