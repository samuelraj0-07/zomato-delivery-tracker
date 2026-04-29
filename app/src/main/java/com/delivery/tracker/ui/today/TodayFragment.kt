package com.delivery.tracker.ui.today

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.R
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.databinding.FragmentTodayBinding
import com.delivery.tracker.ocr.JsonTripParser
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.TodayViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner

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
        // Date label — tap to change when no session is active
        viewModel.selectedDateMillis.observe(viewLifecycleOwner) { millis ->
            binding.tvDate.text = DateUtils.formatDate(millis)
        }

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

                if (summary.isSessionEnded) {
                    rowActualRate.visibility = View.VISIBLE
                    rowDeadKm.visibility     = View.VISIBLE
                    tvRateActual.text = if (summary.ratePerKmActual > 0)
                        FormatUtils.formatRate(summary.ratePerKmActual)
                    else "—"
                    tvDeadKm.text = FormatUtils.formatKm(summary.deadKm)
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
                    tvDate.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    tvDate.isClickable = false

                    if (session.isEnded) {
                        // Day ended — both buttons grey, fully disabled
                        setButtonTint(btnStartDay, R.color.surface_variant)
                        setButtonTint(btnEndDay,   R.color.surface_variant)
                        btnStartDay.isEnabled = false
                        btnEndDay.isEnabled   = false
                    } else {
                        // Day running — Start blue (locked/done), End red (action needed)
                        setButtonTint(btnStartDay, R.color.btn_locked)
                        setButtonTint(btnEndDay,   R.color.primary)
                        btnStartDay.isEnabled = false
                        btnEndDay.isEnabled   = true
                    }
                } else {
                    // No session — Start ready, End disabled grey
                    etStartOdometer.isEnabled = true
                    setButtonTint(btnStartDay, R.color.primary)
                    setButtonTint(btnEndDay,   R.color.surface_variant)
                    btnStartDay.isEnabled = true
                    btnEndDay.isEnabled   = false
                    tvDate.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        0, 0, android.R.drawable.ic_menu_edit, 0
                    )
                    tvDate.isClickable = true
                }
            }
        }

        viewModel.sessionStarted.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Day started! 🚀", Toast.LENGTH_SHORT).show()
        }

        viewModel.sessionEnded.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Day ended! Actual ₹/km updated ✅", Toast.LENGTH_SHORT).show()
        }

        viewModel.odometerError.observe(viewLifecycleOwner) { message ->
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Odometer Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setupListeners() {
        // ── Date picker (only when no active session) ──────────────────────
        binding.tvDate.setOnClickListener {
            if (viewModel.activeSession.value != null) return@setOnClickListener
            val cal = Calendar.getInstance().apply {
                timeInMillis = viewModel.selectedDateMillis.value ?: System.currentTimeMillis()
            }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    viewModel.setSelectedDate(picked)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                // Don't allow future dates
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        // ── ₹/km live preview ─────────────────────────────────────────────
        val updatePreview = {
            val pay  = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val dist = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0
            binding.tvRatePreview.text = if (pay > 0 && dist > 0)
                "₹/km: ${FormatUtils.formatRate(pay / dist)}" else "₹/km: —"
        }
        binding.etOrderPay.addTextChangedListener { updatePreview() }
        binding.etDistance.addTextChangedListener { updatePreview() }

        // ── Start / End day ────────────────────────────────────────────────
        binding.btnStartDay.setOnClickListener {
            val odometer = binding.etStartOdometer.text.toString().toDoubleOrNull()
            if (odometer == null || odometer <= 0) {
                Toast.makeText(requireContext(), "Enter valid start odometer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Pass the selected date so past sessions get the right date
            viewModel.startDay(odometer, viewModel.selectedDateMillis.value ?: System.currentTimeMillis())
        }

        binding.btnEndDay.setOnClickListener {
            val odometer = binding.etEndOdometer.text.toString().toDoubleOrNull()
            if (odometer == null || odometer <= 0) {
                Toast.makeText(requireContext(), "Enter valid end odometer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.endDay(odometer)
        }

        // ── JSON import ────────────────────────────────────────────────────
        binding.btnScan.setOnClickListener {
            showJsonInputDialog()
        }

        // ADD this inside setupListeners(), after the btnScan click listener:
        binding.btnAddExtraPay.setOnClickListener {
            addExtraPayRow()
        }

        // ── Manual trip add ────────────────────────────────────────────────
        binding.btnAddTrip.setOnClickListener {
            val restaurant = binding.etRestaurant.text.toString().trim()
            val time       = binding.etAssignedTime.text.toString().trim()
            val orderPay   = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val distance   = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0

            // REMOVED: tips, surge, incentive hardcoded reads
            // ADDED: collect from dynamic rows instead
            val extraPays = collectExtraPays()

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
            hint = """Paste JSON list — each object = one separate trip:
[
  {
    "restaurant_name": "Kati Central",
    "order_assigned_time": "8:02 pm",
    "order_pay": 97.99,
    "extra_pay": { "incentive_pay": 5.0 },
    "total_distance_km": 5.5
  },
  {
    "restaurant_name": "Biryani Blues",
    "order_assigned_time": "9:15 pm",
    "order_pay": 60.0,
    "total_distance_km": 3.2
  }
]"""
            minLines     = 6
            maxLines     = 20
            isSingleLine = false
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Import trips from JSON")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val jsonText = input.text.toString().trim()
                if (jsonText.isEmpty()) {
                    Toast.makeText(requireContext(), "Nothing to import", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val results = JsonTripParser.parseAll(jsonText)
                if (results == null) {
                    Toast.makeText(requireContext(), "Invalid JSON. Check format and try again.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                viewModel.addTripsFromOcrList(results)
                Toast.makeText(
                    requireContext(),
                    "Imported ${results.size} trip${if (results.size != 1) "s" else ""} ✅",
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
            containerExtraPays.removeAllViews()   
            tvRatePreview.text = "₹/km: —"
        }
    }

    private fun setButtonTint(button: com.google.android.material.button.MaterialButton, colorRes: Int) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorRes)
        )
    }

    // ADD this new function:
    private fun addExtraPayRow(keyHint: String = "", amountHint: String = "") {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }

        // Key name spinner with common suggestions
        val keyOptions = arrayOf(
            "customer_tip", "surge_pay", "incentive_pay",
            "rain_bonus", "long_distance_pay", "peak_pay", "other"
        )
        val spinnerAdapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, keyOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val spinner = Spinner(ctx).apply {
            adapter = spinnerAdapter
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            if (keyHint.isNotEmpty()) {
                val idx = keyOptions.indexOf(keyHint)
                if (idx >= 0) setSelection(idx)
            }
        }

        // Amount field
        val etAmount = android.widget.EditText(ctx).apply {
            hint = "₹"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 14f
            if (amountHint.isNotEmpty()) setText(amountHint)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_primary))
            setHintTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
        }

        // Remove button
        val btnRemove = android.widget.Button(ctx).apply {
            text = "✕"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { binding.containerExtraPays.removeView(row) }
            background = null
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.negative))
        }

        row.addView(spinner)
        row.addView(etAmount)
        row.addView(btnRemove)
        binding.containerExtraPays.addView(row)
    }

    private fun collectExtraPays(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val container = binding.containerExtraPays
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? LinearLayout ?: continue
            val spinner  = row.getChildAt(0) as? Spinner ?: continue
            val etAmount = row.getChildAt(1) as? android.widget.EditText ?: continue
            val key    = spinner.selectedItem?.toString() ?: continue
            val amount = etAmount.text.toString().toDoubleOrNull() ?: continue
            if (amount > 0) result[key] = amount
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
