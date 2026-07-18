package com.example.exotrade.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.admin.AdminActivity
import com.example.exotrade.activities.listings.BrowseListingsFragment
import com.example.exotrade.activities.listings.CreateListingFragment
import com.example.exotrade.activities.messaging.InboxFragment
import com.example.exotrade.activities.profile.ProfileFragment
import com.example.exotrade.databinding.ActivityMainHostBinding
import com.example.exotrade.utils.Helpers

class MainHostActivity : BaseActivity() {

    private lateinit var binding: ActivityMainHostBinding
    
    private var browseListingsFragment: BrowseListingsFragment? = null
    private var createListingFragment: CreateListingFragment? = null
    private var inboxFragment: InboxFragment? = null
    private var profileFragment: ProfileFragment? = null

    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restoreFragments()
        setupBottomNavigation()
        setupOnBackPressed()
        
        handleIntent(intent)
    }

    private fun restoreFragments() {
        val fm = supportFragmentManager
        browseListingsFragment = fm.findFragmentByTag("tab_home") as? BrowseListingsFragment
        inboxFragment = fm.findFragmentByTag("tab_messages") as? InboxFragment
        createListingFragment = fm.findFragmentByTag("tab_add") as? CreateListingFragment
        profileFragment = fm.findFragmentByTag("tab_profile") as? ProfileFragment

        if (browseListingsFragment == null) {
            browseListingsFragment = BrowseListingsFragment()
            inboxFragment = InboxFragment()
            createListingFragment = CreateListingFragment()
            profileFragment = ProfileFragment()

            fm.beginTransaction()
                .add(R.id.fragmentContainer, profileFragment!!, "tab_profile").hide(profileFragment!!)
                .add(R.id.fragmentContainer, createListingFragment!!, "tab_add").hide(createListingFragment!!)
                .add(R.id.fragmentContainer, inboxFragment!!, "tab_messages").hide(inboxFragment!!)
                .add(R.id.fragmentContainer, browseListingsFragment!!, "tab_home")
                .commit()
            currentFragment = browseListingsFragment
        } else {
            // All fragments restored from FM. Find which one was visible, or default to home.
            currentFragment = listOf(browseListingsFragment, inboxFragment, createListingFragment, profileFragment)
                .find { it?.isHidden == false } ?: browseListingsFragment
            
            // Ensure bottom nav matches the restored visible fragment
            val selectedId = when (currentFragment) {
                browseListingsFragment -> R.id.nav_home
                inboxFragment -> R.id.nav_messages
                createListingFragment -> R.id.nav_add
                profileFragment -> R.id.nav_profile
                else -> R.id.nav_home
            }
            binding.bottomNavigation.selectedItemId = selectedId
        }
    }

    private fun setupBottomNavigation() {
        val session = ExoTradeApplication.container.sessionRepository
        binding.bottomNavigation.menu.findItem(R.id.nav_admin)?.isVisible = session.isAdmin()
        Helpers.prepareBottomNav(binding.bottomNavigation)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    browseListingsFragment?.let { switchToFragment(it) }
                    true
                }
                R.id.nav_messages -> {
                    inboxFragment?.let { switchToFragment(it) }
                    true
                }
                R.id.nav_add -> {
                    createListingFragment?.let { switchToFragment(it) }
                    true
                }
                R.id.nav_profile -> {
                    profileFragment?.let { switchToFragment(it) }
                    true
                }
                R.id.nav_admin -> {
                    startActivity(Intent(this, AdminActivity::class.java))
                    false // Don't select it as a tab
                }
                else -> false
            }
        }
    }

    private fun switchToFragment(target: Fragment) {
        if (currentFragment == target) return
        
        val transaction = supportFragmentManager.beginTransaction()
        currentFragment?.let { transaction.hide(it) }
        transaction.show(target).commit()
        currentFragment = target
    }

    fun switchTab(itemId: Int) {
        binding.bottomNavigation.selectedItemId = itemId
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFragment != browseListingsFragment) {
                    switchTab(R.id.nav_home)
                } else {
                    moveTaskToBack(true)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val initialTab = intent.getIntExtra("initial_tab", -1)
        if (initialTab != -1) {
            switchTab(initialTab)
        }
    }
    
    override fun onResume() {
        super.onResume()
        Helpers.updateUnreadBadge(binding.bottomNavigation)
        Helpers.updateNavProfileIcon(binding.bottomNavigation)
    }
}
