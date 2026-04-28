package com.delivery.tracker.ui.history

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.databinding.FragmentHistoryBinding
import com.delivery.tracker.ocr.JsonTripParser
import com.delivery.tracker.ui.today.TripAdapter
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.HistoryViewMode
import com.delivery.tracker.viewmodel.HistoryViewModel
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var tripAdapter: TripAdapter
    private var currentMillis = System.currentTimeMillis()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        tripAdapter = TripAdapter(
            onDelete = { trip -> viewModel.deleteTrip(trip) },
            getSubOrders = { _, _ -> }
        )
        binding.rvHistoryTrips.apply {
            adapter = tripAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            binding.apply {
                tvPeriodLabel.text = summary.periodLabel
                tvHTrips.text = "${summary.totalTrips}"
                tvHBase.text = FormatUtils.formatMoney(summary.totalOrderPay)
                tvHExtras.text = FormatUtils.formatMoney(summary.totalExtras)
                tvHRateScreenshot.text = FormatUtils.formatRate(summary.ratePerKmScreenshot)
                tvHRateActual.text = if (summary.ratePerKmActual > 0)
                    FormatUtils.formatRate(summary.ratePerKmActual) else "—"
                tvHFuel.text = FormatUtils.formatMoney(summary.totalFuelSpent)
                tvHTds.text = FormatUtils.formatMoney(summary.totalTds)
                tvHNet.text = FormatUtils.formatBalance(summary.netRemaining)
                tvHNet.setTextColor(
                    if (summary.netRemaining >= 0)
                        requireContext().getColor(com.delivery.tracker.R.color.positive)
                    else
                        requireContext().getColor(com.delivery.tracker.R.color.negative)
                )
            }
        }

        viewModel.trips.observe(viewLifecycleOwner) { trips ->
            tripAdapter.submitList(trips)
        }

        // Show "Add Trip" button only in Day mode
        viewModel.viewMode.observe(viewLifecycleOwner) { mode ->
            binding.btnAddTripHistory.visibility =
                if (mode == HistoryViewMode.DAY) View.VISIBLE else View.GONE
        }

        viewModel.tripAdded.observe(viewLifecycleOwner) { count ->
            Toast.makeText(
                requireContext(),
                "Added $count trip${if (count != 1) "s" else ""} ✅",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupListeners() {
        binding.tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val mode = when (tab.position) {
                    0 -> HistoryViewMode.DAY
                    1 -> HistoryViewMode.WEEK
                    else -> HistoryViewMode.MONTH
                }
                viewModel.setViewMode(mode)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnPrev.setOnClickListener {
            currentMillis = shiftDate(currentMillis, -1)
            viewModel.setSelectedDate(currentMillis)
        }

        binding.btnNext.setOnClickListener {
            currentMillis = shiftDate(currentMillis, 1)
            viewModel.setSelectedDate(currentMillis)
        }

        binding.btnAddTripHistory.setOnClickListener {
            showAddTripDialog()
        }
    }

    private fun showAddTripDialog() {
        val options = arrayOf("📋 Paste JSON", "✏️ Enter manually")
        AlertDialog.Builder(requireContext())
            .setTitle("Add trip to ${viewModel.summary.value?.periodLabel ?: "this day"}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showJsonInputDialog()
                    1 -> showManualInputDialog()
                }
            }
            .show()
    }

    private fun showJsonInputDialog() {
        val input = EditText(requireContext()).apply {
            hint = """[
  {
    "restaurant_name": "Kati Central",
    "order_assigned_time": "8:02 pm",
    "order_pay": 97.99,
    "extra_pay": { "incentive_pay": 5.0 },
    "total_distance_km": 5.5
  }
]"""
            minLines     = 6
            maxLines     = 20
            isSingleLine = false
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Paste JSON trips")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val results = JsonTripParser.parseAll(text)
                if (results == null) {
                    Toast.makeText(requireContext(), "Invalid JSON", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                viewModel.addTripsFromOcrList(results)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualInputDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        fun field(hint: String, numeric: Boolean = false) = EditText(ctx).apply {
            this.hint = hint
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }

        val etRestaurant = field("Restaurant name")
        val etTime       = field("Assigned time (e.g. 8:02 PM)")
        val etPay        = field("Order pay (₹)", numeric = true)
        val etDist       = field("Distance (km)", numeric = true)
        val etTips       = field("Tips ₹ (optional)", numeric = true)
        val etSurge      = field("Surge ₹ (optional)", numeric = true)
        val etIncentive  = field("Incentive ₹ (optional)", numeric = true)

        listOf(etRestaurant, etTime, etPay, etDist, etTips, etSurge, etIncentive)
            .forEach { layout.addView(it) }

        // Live ₹/km preview
        val tvPreview = android.widget.TextView(ctx).apply {
            text = "₹/km: —"
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.warning))
        }
        layout.addView(tvPreview)

        val updatePreview = {
            val pay  = etPay.text.toString().toDoubleOrNull() ?: 0.0
            val dist = etDist.text.toString().toDoubleOrNull() ?: 0.0
            tvPreview.text = if (pay > 0 && dist > 0)
                "₹/km: ${FormatUtils.formatRate(pay / dist)}" else "₹/km: —"
        }
        etPay.addTextChangedListener { updatePreview() }
        etDist.addTextChangedListener { updatePreview() }

        AlertDialog.Builder(ctx)
            .setTitle("Add trip manually")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val restaurant = etRestaurant.text.toString().trim()
                val time       = etTime.text.toString().trim()
                val pay        = etPay.text.toString().toDoubleOrNull() ?: 0.0
                val dist       = etDist.text.toString().toDoubleOrNull() ?: 0.0

                if (restaurant.isEmpty() || pay <= 0 || dist <= 0) {
                    Toast.makeText(ctx, "Fill in restaurant, pay and distance", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val extras = buildMap<String, Double> {
                    etTips.text.toString().toDoubleOrNull()?.takeIf { it > 0 }?.let { put("customer_tip", it) }
                    etSurge.text.toString().toDoubleOrNull()?.takeIf { it > 0 }?.let { put("surge_pay", it) }
                    etIncentive.text.toString().toDoubleOrNull()?.takeIf { it > 0 }?.let { put("incentive_pay", it) }
                }

                viewModel.addTripManual(restaurant, time, pay, dist, extras)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shiftDate(millis: Long, direction: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        when (viewModel.viewMode.value) {
            HistoryViewMode.DAY   -> cal.add(Calendar.DAY_OF_MONTH, direction)
            HistoryViewMode.WEEK  -> cal.add(Calendar.WEEK_OF_YEAR, direction)
            HistoryViewMode.MONTH -> cal.add(Calendar.MONTH, direction)
            else                  -> cal.add(Calendar.DAY_OF_MONTH, direction)
        }
        return cal.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
