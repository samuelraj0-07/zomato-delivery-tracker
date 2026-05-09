package com.delivery.tracker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.delivery.tracker.R
import com.delivery.tracker.databinding.ActivityMainBinding
import com.delivery.tracker.ui.expenses.ExpensesFragment
import com.delivery.tracker.ui.history.HistoryFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector

    // ── Export/Import launchers ──────────────────────────────────────────────
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) performExport(uri)
    }
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) performImport(uri)
    }

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
        setupDrawer()

        // ── Swipe gesture detector ─────────────────────────────────────────
        gestureDetector = GestureDetector(
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
                    if (abs(deltaX) < SWIPE_MIN_DISTANCE) return false
                    if (abs(velocityX) < SWIPE_MIN_VELOCITY) return false

                    // +1 = swipe left (forward), -1 = swipe right (backward)
                    val direction = if (deltaX < 0) 1 else -1

                    // ── Step 1: try inner tabs in the current fragment first ───────────
                    val navHostFrag = supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFrag = navHostFrag
                        ?.childFragmentManager?.fragments?.firstOrNull()

                    val innerConsumed = when (currentFrag) {
                        is HistoryFragment  -> currentFrag.swipeInnerTab(direction)
                        is ExpensesFragment -> currentFrag.swipeInnerTab(direction)
                        else                -> false
                    }
                    if (innerConsumed) return true

                    // ── Step 2: inner was at boundary — move main bottom-nav tab ──────
                    val currentId  = navController.currentDestination?.id ?: return false
                    val currentIdx = tabOrder.indexOf(currentId)
                    if (currentIdx == -1) return false

                    val targetIdx = currentIdx + direction
                    if (targetIdx < 0 || targetIdx >= tabOrder.size) return false

                    binding.bottomNav.selectedItemId = tabOrder[targetIdx]
                    return true
                }
            }
        )

    }

    private fun setupDrawer() {
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_export -> {
                    exportLauncher.launch("delivery_tracker_backup.json")
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_import -> {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                    binding.drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
    }

    private fun performExport(uri: Uri) {
        val db = com.delivery.tracker.data.db.AppDatabase.getInstance(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = com.delivery.tracker.utils.DataExporter.export(db)
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Data exported ✅", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        val db = com.delivery.tracker.data.db.AppDatabase.getInstance(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = com.delivery.tracker.utils.DataExporter.import(db, json)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@MainActivity, "Data imported ✅ Restarting…", Toast.LENGTH_SHORT).show()
                        // Issue 4: Restart the app so all LiveData/ViewModels reload fresh
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } else {
                        Toast.makeText(this@MainActivity, "Import failed — invalid file", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // dispatchTouchEvent intercepts ALL touches at Activity level,
    // BEFORE any fragment or view consumes them — this is what makes swipe reliable
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }
}