package com.delivery.tracker.ui

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.delivery.tracker.R
import com.delivery.tracker.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Tab order must match bottom_nav_menu order exactly
    private val tabOrder = listOf(
        R.id.todayFragment,
        R.id.historyFragment,
        R.id.expensesFragment,
        R.id.analyticsFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        // ── Swipe gesture detector ─────────────────────────────────────────
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                private val SWIPE_MIN_DISTANCE   = 100f  // minimum px moved
                private val SWIPE_MIN_VELOCITY   = 400f  // minimum px/sec speed

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val e1 = e1 ?: return false

                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y

                    // Ignore if swipe is more vertical than horizontal
                    if (abs(deltaY) > abs(deltaX)) return false

                    // Ignore if swipe is too short or too slow
                    if (abs(deltaX) < SWIPE_MIN_DISTANCE) return false
                    if (abs(velocityX) < SWIPE_MIN_VELOCITY) return false

                    val currentId  = navController.currentDestination?.id ?: return false
                    val currentIdx = tabOrder.indexOf(currentId)
                    if (currentIdx == -1) return false

                    val targetIdx = if (deltaX < 0) {
                        // Swipe LEFT → go to next tab
                        currentIdx + 1
                    } else {
                        // Swipe RIGHT → go to previous tab
                        currentIdx - 1
                    }

                    // Clamp — no wrap-around, just stop at the ends
                    if (targetIdx < 0 || targetIdx >= tabOrder.size) return false

                    val targetId = tabOrder[targetIdx]

                    // Use the same behaviour as tapping the bottom nav icon
                    binding.bottomNav.selectedItemId = targetId
                    return true
                }
            }
        )

        // Attach the gesture detector to the fragment container
        binding.navHostFragment.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else {
                v.performClick()
                false
            }
        }
    }
}