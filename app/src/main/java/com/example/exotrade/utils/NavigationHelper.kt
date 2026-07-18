package com.example.exotrade.utils

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.activities.admin.AdminActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Utility class to set up {@link BottomNavigationView} and handle tab transitions.
 * Centralizes the logic for switching between main activities and applying snappy transitions.
 * Note: Main tabs are now fragments inside {@link MainHostActivity}.
 */
object NavigationHelper {

    private var lastNavTime = 0L

    /**
     * Configures the BottomNavigationView with item listeners and visibility logic.
     *
     * @param activity       The calling activity context.
     * @param bottomNav      The navigation view to configure.
     * @param selectedItemId The menu item ID to mark as selected.
     */
    fun setup(activity: AppCompatActivity, bottomNav: BottomNavigationView, selectedItemId: Int) {
        val session = ExoTradeApplication.container.sessionRepository
        bottomNav.menu.findItem(R.id.nav_admin)?.isVisible = session.isAdmin()
        Helpers.prepareBottomNav(bottomNav)

        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            val now = System.currentTimeMillis()
            if (now - lastNavTime < 400) return@setOnItemSelectedListener false
            
            val itemId = item.itemId
            
            // For MainHostActivity tabs, we redirect to the host activity
            val isTab = itemId == R.id.nav_home || itemId == R.id.nav_messages || 
                         itemId == R.id.nav_add || itemId == R.id.nav_profile

            if (isTab) {
                if (activity is MainHostActivity) {
                    // This case should be handled by the Activity itself, but we keep it safe
                    return@setOnItemSelectedListener true
                }
                
                lastNavTime = now
                val intent = Intent(activity, MainHostActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("initial_tab", itemId)
                }
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
                return@setOnItemSelectedListener true
            }

            val target: Class<*> = when (itemId) {
                R.id.nav_admin -> AdminActivity::class.java
                else -> return@setOnItemSelectedListener false
            }

            // If we are already in the PRIMARY activity for this tab, do nothing
            if (activity::class.java == target) {
                return@setOnItemSelectedListener true 
            }
            
            lastNavTime = now
            val intent = Intent(activity, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0) 

            true
        }
    }
}
