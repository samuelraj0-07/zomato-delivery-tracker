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
import com.delivery.tracker.data.model.DailySession
import androidx.fragment.app.activityViewModels
import com.delivery.tracker.viewmodel.SharedViewModel

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
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
        sharedViewModel.selectedDateMillis.observe(viewLifecycleOwner) { millis ->
            currentMillis = millis
            viewModel.setSelectedDate(millis)
        }
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            binding.apply {
                tvPeriodLabel.text = summary.periodLabel
                tvHTrips.text = "${summary.totalTrips}"
                tvHBase.text = FormatUtils.formatMoney(summary.totalOrderPay)
                tvHExtras.text = FormatUtils.formatMoney(summary.totalExtras)
                tvHRateScreenshot.text = FormatUtils.formatRate(summary.ratePerKmScreenshot)
                tvHRateActual.text = if (summary.ratePerKmActual > 0)
                    FormatUtils.formatRate(summary.ratePerKmActual) else "—"
                tvHFuel.text    = FormatUtils.formatMoney(summary.fuelAllocated)
                tvHService.text = FormatUtils.formatMoney(summary.serviceAllocated)
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
            if (viewModel.viewMode.value == HistoryViewMode.DAY
                && trips.isEmpty()
                && viewModel.daySession.value == null
            ) {
                binding.tvPeriodLabel.text =
                    "${viewModel.summary.value?.periodLabel ?: ""}  ·  No rides recorded"
            }
            // Refresh app distance whenever trips change
            if (viewModel.viewMode.value == HistoryViewMode.DAY && viewModel.daySession.value != null) {
                val appDist = viewModel.getDayAppDistance()
                binding.tvOdoAppDistance.text = if (appDist > 0)
                    String.format("%.1f km", appDist) else "—"
            }
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
                binding.tvOdoStart.text = String.format("%.1f km", session.startOdometer)
                binding.tvOdoEnd.text = if (session.endOdometer > 0)
                    String.format("%.1f km", session.endOdometer) else "—"
                binding.tvOdoDistance.text = if (session.actualDistance > 0)
                    String.format("%.2f km", session.actualDistance) else "—"
                // App distance = sum of screenshotDistance from all trips this day
                val appDist = viewModel.getDayAppDistance()
                binding.tvOdoAppDistance.text = if (appDist > 0)
                    String.format("%.1f km", appDist) else "—"
                binding.rowOdoAppDistance.visibility = View.VISIBLE
            } else {
                binding.cardOdometer.visibility = View.GONE
                binding.rowOdoAppDistance.visibility = View.GONE
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
            sharedViewModel.setSelectedDate(currentMillis) 
        }

        binding.btnNext.setOnClickListener {
            currentMillis = shiftDate(currentMillis, 1)
            viewModel.setSelectedDate(currentMillis)
            sharedViewModel.setSelectedDate(currentMillis) 
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

        binding.btnDeleteDay.setOnClickListener {
            val session = viewModel.daySession.value ?: return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("🗑️ Delete this day?")
                .setMessage("This will permanently delete the session and ALL trips for this day. This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteDayEntries()
                    Toast.makeText(requireContext(), "Day deleted ✅", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
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
                sharedViewModel.setSelectedDate(currentMillis)
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
        val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
        val weeks = DateUtils.weeksOverlappingMonth(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)
        )
        val labels = weeks.map { it.first }.toTypedArray()
        val selectedIdx = weeks.indexOfFirst {
            it.second == DateUtils.startOfWeek(currentMillis)
        }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Select week")
            .setSingleChoiceItems(labels, selectedIdx) { dialog, which ->
                currentMillis = weeks[which].second
                viewModel.setSelectedDate(currentMillis)
                sharedViewModel.setSelectedDate(currentMillis)
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
                sharedViewModel.setSelectedDate(currentMillis)
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

    // ── All dialog methods below are unchanged from original ──────────────

    private fun showAddTripDialog() {
        val options = arrayOf("📋 Paste JSON", "✏️ Enter manually", "🎁 Add incentive")
        AlertDialog.Builder(requireContext())
            .setTitle("Add to ${viewModel.summary.value?.periodLabel ?: "this day"}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showJsonInputDialog()
                    1 -> showManualInputDialog()
                    2 -> showAddIncentiveDialog()
                }
            }
            .show()
    }

    private fun showAddIncentiveDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tvInfo = android.widget.TextView(ctx).apply {
            text = "Add incentive earned for this day. This is stored as an extra pay on a trip for this day."
            textSize = 13f
            setPadding(0, 0, 0, 16)
        }

        // Incentive type spinner
        val incentiveTypes = arrayOf(
            "incentive_pay", "peak_pay", "rain_bonus",
            "long_distance_pay", "special_event_bonus", "other"
        )
        val spinnerLabel = android.widget.TextView(ctx).apply {
            text = "Incentive type"
            textSize = 12f
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
        }
        val spinner = android.widget.Spinner(ctx).apply {
            adapter = android.widget.ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                incentiveTypes
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        val etAmount = android.widget.EditText(ctx).apply {
            hint = "Amount (₹)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val etNote = android.widget.EditText(ctx).apply {
            hint = "Note (optional — e.g. 'Diwali special')"
        }

        layout.addView(tvInfo)
        layout.addView(spinnerLabel)
        layout.addView(spinner)
        layout.addView(etAmount)
        layout.addView(etNote)

        AlertDialog.Builder(ctx)
            .setTitle("🎁 Add Incentive")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(ctx, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = spinner.selectedItem.toString()
                val note = etNote.text.toString().trim()
                val label = if (note.isEmpty()) type else "$type ($note)"
                // Store incentive as a standalone trip with ₹0 order pay and
                // the incentive as an extra pay entry
                viewModel.addTripManual(
                    restaurantName = "🎁 ${label}",
                    assignedTime   = java.text.SimpleDateFormat(
                        "h:mm a", java.util.Locale.getDefault()
                    ).format(java.util.Date()),
                    orderPay       = 0.0,
                    distance       = 0.0,
                    extraPays      = mapOf(type to amount)
                )
                Toast.makeText(ctx, "Incentive ₹${amount.toInt()} added ✅", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
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
        val etPay        = field("Order pay (₹)", numeric = true)
        val etDist       = field("Distance (km)", numeric = true)

        // ── Time picker for assigned time ──────────────────────────────────
        val selectedHour   = intArrayOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        val selectedMinute = intArrayOf(java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE))
        fun fmtTime(h: Int, m: Int): String {
            val ampm = if (h < 12) "AM" else "PM"
            val h12  = if (h % 12 == 0) 12 else h % 12
            return String.format("%d:%02d %s", h12, m, ampm)
        }
        val btnTimePicker = android.widget.Button(ctx).apply {
            text = "🕐 Assigned time: ${fmtTime(selectedHour[0], selectedMinute[0])}"
            background = null
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.primary))
            textSize = 13f
            setOnClickListener {
                android.app.TimePickerDialog(ctx, { _, h, m ->
                    selectedHour[0]   = h
                    selectedMinute[0] = m
                    text = "🕐 Assigned time: ${fmtTime(h, m)}"
                }, selectedHour[0], selectedMinute[0], false).show()
            }
        }

        listOf(etRestaurant, etPay, etDist).forEach { layout.addView(it) }
        layout.addView(btnTimePicker)

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
                val time       = fmtTime(selectedHour[0], selectedMinute[0])
                val pay        = etPay.text.toString().toDoubleOrNull() ?: 0.0
                val dist       = etDist.text.toString().toDoubleOrNull() ?: 0.0

                if (restaurant.isEmpty() || pay <= 0 || dist <= 0) {
                    Toast.makeText(ctx, "Fill in restaurant, pay and distance", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

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

    /**
     * Called by MainActivity when a horizontal swipe is detected.
     * Moves Day→Week→Month (direction=+1) or Month→Week→Day (direction=-1).
     * Returns true if the tab was moved, false if already at the boundary.
     */
    fun swipeInnerTab(direction: Int): Boolean {
        val tab = binding.tabMode
        val current = tab.selectedTabPosition          // 0=Day, 1=Week, 2=Month
        val target  = current + direction
        if (target < 0 || target >= tab.tabCount) return false
        tab.getTabAt(target)?.select()
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}