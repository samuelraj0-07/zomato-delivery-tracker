package com.delivery.tracker.ui.history

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.databinding.FragmentHistoryBinding
import com.delivery.tracker.ocr.JsonTripParser
import com.delivery.tracker.ui.today.TripAdapter
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.HistoryViewMode
import com.delivery.tracker.viewmodel.HistoryViewModel
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.widget.EditText
import com.delivery.tracker.data.model.DailySession

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

        viewModel.daySession.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                binding.cardOdometer.visibility = View.VISIBLE
                binding.tvOdoStart.text = "${session.startOdometer} km"
                binding.tvOdoEnd.text = if (session.endOdometer > 0) "${session.endOdometer} km" else "—"
                binding.tvOdoDistance.text = if (session.actualDistance > 0)
                    "${session.actualDistance} km" else "—"
            } else {
                binding.cardOdometer.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val mode = when (tab.position) {
                    0    -> HistoryViewMode.DAY
                    1    -> HistoryViewMode.WEEK
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

        // ── Tap period label to jump via picker ───────────────────────────
        binding.tvPeriodLabel.setOnClickListener {
            when (viewModel.viewMode.value) {
                HistoryViewMode.DAY   -> showDayPicker()
                HistoryViewMode.WEEK  -> showWeekPicker()
                HistoryViewMode.MONTH -> showMonthPicker()
                else                  -> showDayPicker()
            }
        }
        // Make it obvious the label is tappable
        binding.tvPeriodLabel.isClickable = true
        binding.tvPeriodLabel.paintFlags =
            binding.tvPeriodLabel.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG

        binding.btnAddTripHistory.setOnClickListener {
            showAddTripDialog()
        }

        binding.btnEditOdometer.setOnClickListener {
            val session = viewModel.daySession.value ?: return@setOnClickListener
            showEditOdometerDialog(session)
        }
    }

    // ── DAY: standard DatePickerDialog showing a full calendar ────────────
    private fun showDayPicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                currentMillis = picked
                viewModel.setSelectedDate(currentMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis() // no future dates
        }.show()
    }

    // ── WEEK: list all Mon–Sun week ranges in the current month ───────────
    private fun showWeekPicker() {
        val weeks = getWeeksOfMonth(currentMillis)   // list of Pair(label, startMillis)
        val labels = weeks.map { it.first }.toTypedArray()

        // Pre-select the week that contains currentMillis
        val currentWeekStart = DateUtils.startOfWeekInMonth(currentMillis)
        val selectedIdx = weeks.indexOfFirst { it.second == currentWeekStart }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Select week")
            .setSingleChoiceItems(labels, selectedIdx) { dialog, which ->
                currentMillis = weeks[which].second
                viewModel.setSelectedDate(currentMillis)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── MONTH: two NumberPickers — month name + year ───────────────────────
    private fun showMonthPicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        val currentYear  = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)  // 0-based

        val monthNames = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )

        // Build a small layout with two side-by-side NumberPickers
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
            setPadding(32, 24, 32, 8)
        }

        val monthPicker = NumberPicker(requireContext()).apply {
            minValue     = 0
            maxValue     = 11
            displayedValues = monthNames
            value        = currentMonth
            wrapSelectorWheel = true
        }

        val yearPicker = NumberPicker(requireContext()).apply {
            minValue = 2020
            maxValue = currentYear          // can't go into the future
            value    = currentYear
            wrapSelectorWheel = false
        }

        layout.addView(monthPicker)
        layout.addView(yearPicker)

        AlertDialog.Builder(requireContext())
            .setTitle("Select month")
            .setView(layout)
            .setPositiveButton("Go") { _, _ ->
                val picked = Calendar.getInstance().apply {
                    set(yearPicker.value, monthPicker.value, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                currentMillis = picked
                viewModel.setSelectedDate(currentMillis)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Returns all Mon-Sun week ranges inside the month of [millis],
     * each as Pair(displayLabel, weekStartMillis).
     * E.g. [("1-6 Apr", startMillis), ("7-13 Apr", startMillis), ...]
     */
    private fun getWeeksOfMonth(millis: Long): List<Pair<String, Long>> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val year  = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        // Start from day 1 of the month
        val dayCal = Calendar.getInstance().apply {
            set(year, month, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val weeks = mutableListOf<Pair<String, Long>>()
        val fmt   = SimpleDateFormat("MMM", Locale.getDefault())

        while (dayCal.get(Calendar.MONTH) == month) {
            val weekStart = DateUtils.startOfWeekInMonth(dayCal.timeInMillis)
            val weekEnd   = DateUtils.endOfWeekInMonth(dayCal.timeInMillis)

            val startDay = Calendar.getInstance().apply { timeInMillis = weekStart }
                .get(Calendar.DAY_OF_MONTH)
            val endDay   = Calendar.getInstance().apply { timeInMillis = weekEnd }
                .get(Calendar.DAY_OF_MONTH)
            val monthName = fmt.format(dayCal.time)

            val label = "$startDay–$endDay $monthName"

            // Avoid duplicates (multiple days in same week)
            if (weeks.none { it.second == weekStart }) {
                weeks.add(Pair(label, weekStart))
            }

            dayCal.add(Calendar.WEEK_OF_YEAR, 1)
        }

        return weeks
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

    // ── All dialog methods below are unchanged from original ──────────────

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

        // CHANGED: only add 4 base fields — no more hardcoded tips/surge/incentive
        listOf(etRestaurant, etTime, etPay, etDist).forEach { layout.addView(it) }

        // ── Dynamic extra pays ─────────────────────────────────────────────
        val tvExtrasHeader = android.widget.TextView(ctx).apply {
            text = "Extra Pays"
            textSize = 12f
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
        }
        val extraContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        val btnAddExtra = android.widget.Button(ctx).apply {
            text = "+ Add Extra Pay"
            textSize = 12f
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.primary))
            background = null
        }
        layout.addView(tvExtrasHeader)
        layout.addView(extraContainer)
        layout.addView(btnAddExtra)

        val keyOptions = arrayOf(
            "customer_tip", "surge_pay", "incentive_pay",
            "rain_bonus", "long_distance_pay", "peak_pay", "other"
        )

        btnAddExtra.setOnClickListener {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val spinner = android.widget.Spinner(ctx).apply {
                adapter = android.widget.ArrayAdapter(
                    ctx, android.R.layout.simple_spinner_item, keyOptions
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            }
            val etAmt = android.widget.EditText(ctx).apply {
                hint = "₹"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_primary))
            }
            val btnX = android.widget.Button(ctx).apply {
                text = "✕"
                background = null
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.negative))
                setOnClickListener { extraContainer.removeView(row) }
            }
            row.addView(spinner)
            row.addView(etAmt)
            row.addView(btnX)
            extraContainer.addView(row)
        }

        // ── ₹/km live preview ─────────────────────────────────────────────
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

                // CHANGED: collect from dynamic rows instead of hardcoded fields
                val extras = mutableMapOf<String, Double>()
                for (i in 0 until extraContainer.childCount) {
                    val row     = extraContainer.getChildAt(i) as? LinearLayout ?: continue
                    val spinner = row.getChildAt(0) as? android.widget.Spinner ?: continue
                    val etAmt   = row.getChildAt(1) as? android.widget.EditText ?: continue
                    val key     = spinner.selectedItem?.toString() ?: continue
                    val amount  = etAmt.text.toString().toDoubleOrNull() ?: continue
                    if (amount > 0) extras[key] = amount
                }

                viewModel.addTripManual(restaurant, time, pay, dist, extras)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditOdometerDialog(session: DailySession) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val etStart = EditText(ctx).apply {
            hint = "Start Odometer (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(session.startOdometer.toString())
        }
        val etEnd = EditText(ctx).apply {
            hint = "End Odometer (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (session.endOdometer > 0) session.endOdometer.toString() else "")
        }

        layout.addView(etStart)
        layout.addView(etEnd)

        AlertDialog.Builder(ctx)
            .setTitle("✏️ Edit Odometer")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newStart = etStart.text.toString().toDoubleOrNull()
                val newEnd   = etEnd.text.toString().toDoubleOrNull() ?: 0.0
                if (newStart == null || newStart <= 0) {
                    Toast.makeText(ctx, "Enter valid start odometer", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newEnd > 0 && newEnd <= newStart) {
                    Toast.makeText(ctx, "End must be greater than start", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateSessionOdometer(session, newStart, newEnd)
                Toast.makeText(ctx, "Odometer updated ✅", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}