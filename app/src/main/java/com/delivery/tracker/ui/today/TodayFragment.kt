package com.delivery.tracker.ui.today

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.databinding.FragmentTodayBinding
import com.delivery.tracker.ocr.JsonTripParser
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.TodayViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TodayViewModel by viewModels()
    private lateinit var tripAdapter: TripAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupListeners()
        binding.tvDate.text = DateUtils.formatDate(System.currentTimeMillis())
    }

    private fun setupRecyclerView() {
        tripAdapter = TripAdapter(
            onDelete = { trip -> viewModel.deleteTrip(trip) },
            getSubOrders = { tripId, callback ->
                viewModel.getSubOrdersForTrip(tripId)
                    .observe(viewLifecycleOwner) { callback(it) }
            }
        )
        binding.rvTrips.apply {
            adapter = tripAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        viewModel.todayTrips.observe(viewLifecycleOwner) { trips ->
            tripAdapter.submitList(trips)
            binding.tvTripCount.text = "${trips.size} trips"
        }

        viewModel.todaySummary.observe(viewLifecycleOwner) { summary ->
            binding.apply {
                tvBaseEarnings.text   = FormatUtils.formatMoney(summary.totalOrderPay)
                tvExtras.text         = FormatUtils.formatMoney(summary.totalExtras)
                tvRateScreenshot.text = FormatUtils.formatRate(summary.ratePerKmLive)
                tvTotalDistance.text  = FormatUtils.formatKm(summary.totalScreenshotDistance)
                tvTotalTrips.text     = "${summary.totalTrips}"

                if (summary.isSessionEnded && summary.actualDistance > 0) {
                    rowActualRate.visibility = View.VISIBLE
                    rowDeadKm.visibility     = View.VISIBLE
                    tvRateActual.text = FormatUtils.formatRate(summary.ratePerKmActual)
                    tvDeadKm.text     = FormatUtils.formatKm(summary.deadKm)
                    btnEndDay.isEnabled   = false
                    btnStartDay.isEnabled = false
                } else {
                    rowActualRate.visibility = View.GONE
                    rowDeadKm.visibility     = View.GONE
                }
            }
        }

        viewModel.activeSession.observe(viewLifecycleOwner) { session ->
            binding.apply {
                if (session != null) {
                    etStartOdometer.setText(session.startOdometer.toString())
                    etStartOdometer.isEnabled = false
                    btnStartDay.isEnabled     = false
                    btnEndDay.isEnabled       = !session.isEnded
                } else {
                    etStartOdometer.isEnabled = true
                    btnStartDay.isEnabled     = true
                    btnEndDay.isEnabled       = false
                }
            }
        }

        viewModel.sessionStarted.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Day started! 🚀", Toast.LENGTH_SHORT).show()
        }

        viewModel.sessionEnded.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Day ended! Actual ₹/km updated ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        val updatePreview = {
            val pay  = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val dist = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0
            binding.tvRatePreview.text = if (pay > 0 && dist > 0)
                "₹/km: ${FormatUtils.formatRate(pay / dist)}" else "₹/km: —"
        }
        binding.etOrderPay.addTextChangedListener { updatePreview() }
        binding.etDistance.addTextChangedListener { updatePreview() }

        binding.btnStartDay.setOnClickListener {
            val odometer = binding.etStartOdometer.text.toString().toDoubleOrNull()
            if (odometer == null || odometer <= 0) {
                Toast.makeText(requireContext(), "Enter valid start odometer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.startDay(odometer)
        }

        binding.btnEndDay.setOnClickListener {
            val odometer = binding.etEndOdometer.text.toString().toDoubleOrNull()
            if (odometer == null || odometer <= 0) {
                Toast.makeText(requireContext(), "Enter valid end odometer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.endDay(odometer)
        }

        binding.btnScan.setOnClickListener {
            showJsonInputDialog()
        }

        binding.btnAddTrip.setOnClickListener {
            val restaurant = binding.etRestaurant.text.toString().trim()
            val time       = binding.etAssignedTime.text.toString().trim()
            val orderPay   = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val distance   = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0
            val tips       = binding.etTips.text.toString().toDoubleOrNull() ?: 0.0
            val surge      = binding.etSurge.text.toString().toDoubleOrNull() ?: 0.0
            val incentive  = binding.etIncentive.text.toString().toDoubleOrNull() ?: 0.0

            if (restaurant.isEmpty()) {
                Toast.makeText(requireContext(), "Enter restaurant name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (orderPay <= 0) {
                Toast.makeText(requireContext(), "Enter order pay", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (distance <= 0) {
                Toast.makeText(requireContext(), "Enter distance", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Build extraPays map from the manual form fields
            val extraPays = buildMap<String, Double> {
                if (tips > 0)      put("customer_tip",  tips)
                if (surge > 0)     put("surge_pay",     surge)
                if (incentive > 0) put("incentive_pay", incentive)
            }

            viewModel.addTrip(
                Trip(
                    sessionId          = 0L,
                    restaurantName     = restaurant,
                    assignedTime       = time,
                    orderPay           = orderPay,
                    screenshotDistance = distance,
                    extraPays          = extraPays,
                    dateMillis         = System.currentTimeMillis()
                )
            )
            clearForm()
            Toast.makeText(requireContext(), "Trip added ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showJsonInputDialog() {
        val input = EditText(requireContext()).apply {
            hint = """Paste JSON here, e.g.
[
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
            .setTitle("Import trip from JSON")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val jsonText = input.text.toString().trim()
                if (jsonText.isEmpty()) {
                    Toast.makeText(requireContext(), "Nothing to import", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val result = JsonTripParser.parse(jsonText)
                if (result == null) {
                    Toast.makeText(requireContext(), "Invalid JSON. Check format and try again.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                viewModel.addTripFromOcr(result)
                Toast.makeText(
                    requireContext(),
                    "Imported: ${result.restaurantName} — ₹${result.orderPay}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearForm() {
        binding.apply {
            etRestaurant.text?.clear()
            etAssignedTime.text?.clear()
            etOrderPay.text?.clear()
            etDistance.text?.clear()
            etTips.text?.clear()
            etSurge.text?.clear()
            etIncentive.text?.clear()
            tvRatePreview.text = "₹/km: —"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
