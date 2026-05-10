package com.delivery.tracker.ui.today

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.R
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.databinding.FragmentTodayBinding
import com.delivery.tracker.ocr.JsonTripParser
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.SharedViewModel
import com.delivery.tracker.viewmodel.TodayViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TodayViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var tripAdapter: TripAdapter

    // Calendar state
    private var calYear  = Calendar.getInstance().get(Calendar.YEAR)
    private var calMonth = Calendar.getInstance().get(Calendar.MONTH)

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
        sharedViewModel.selectedDateMillis.observe(viewLifecycleOwner) { millis ->
            viewModel.setSelectedDate(millis)
        }

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
                        FormatUtils.formatRate(summary.ratePerKmActual) else "—"
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
                        setButtonTint(btnStartDay, R.color.surface_variant)
                        setButtonTint(btnEndDay,   R.color.surface_variant)
                        btnStartDay.isEnabled = false
                        btnEndDay.isEnabled   = false
                        if (session.endOdometer > 0) {
                            etEndOdometer.setText(session.endOdometer.toString())
                            etEndOdometer.isEnabled = false
                        }
                    } else {
                        setButtonTint(btnStartDay, R.color.btn_locked)
                        setButtonTint(btnEndDay,   R.color.primary)
                        btnStartDay.isEnabled = false
                        btnEndDay.isEnabled   = true
                    }
                } else {
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
            binding.apply {
                etStartOdometer.setText("")
                etStartOdometer.isEnabled = true
                etEndOdometer.setText("")
                etEndOdometer.isEnabled = true
                tvDate.isClickable = true
                tvDate.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, android.R.drawable.ic_menu_edit, 0
                )
                setButtonTint(btnStartDay, R.color.primary)
                setButtonTint(btnEndDay,   R.color.surface_variant)
                btnStartDay.isEnabled = true
                btnEndDay.isEnabled   = false
                rowActualRate.visibility = View.GONE
                rowDeadKm.visibility     = View.GONE
            }
        }

        viewModel.odometerError.observe(viewLifecycleOwner) { message ->
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Odometer Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        // Feature 1: observe ride days and redraw calendar
        viewModel.rideDaysInMonth.observe(viewLifecycleOwner) { rideDays ->
            renderCalendar(rideDays)
        }
    }

    private fun setupListeners() {
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
                    sharedViewModel.setSelectedDate(picked)
                    viewModel.setSelectedDate(picked)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

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

        binding.btnScan.setOnClickListener { showJsonInputDialog() }

        binding.btnAddExtraPay.setOnClickListener { addExtraPayRow() }

        binding.btnAddTrip.setOnClickListener {
            val restaurant = binding.etRestaurant.text.toString().trim()
            val time       = binding.etAssignedTime.text.toString().trim()
            val orderPay   = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val distance   = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0
            val extraPays  = collectExtraPays()

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

    // ─────────────────────────────────────────────────────────────────────────
    // Feature 1: Monthly calendar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders the mini monthly calendar inside binding.calendarContainer.
     * Days with a recorded session are highlighted in primary red.
     * Tapping a highlighted day navigates to it in the History tab via SharedViewModel.
     */
    private fun renderCalendar(rideDays: Set<Long>) {
        val ctx = requireContext()
        val container = binding.calendarContainer
        container.removeAllViews()

        val colorPrimary   = ContextCompat.getColor(ctx, R.color.primary)
        val colorSurface   = ContextCompat.getColor(ctx, R.color.surface_variant)
        val colorTextMain  = ContextCompat.getColor(ctx, R.color.text_primary)
        val colorTextSec   = ContextCompat.getColor(ctx, R.color.text_secondary)
        val colorToday     = ContextCompat.getColor(ctx, R.color.warning)

        // ── Header row: prev ◀  "May 2026"  ▶ next ──────────────────────────
        val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                                 "Jul","Aug","Sep","Oct","Nov","Dec")
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }
        val btnPrev = TextView(ctx).apply {
            text = "◀"; textSize = 16f; setPadding(16, 8, 16, 8)
            setTextColor(colorPrimary)
            setOnClickListener {
                calMonth--
                if (calMonth < 0) { calMonth = 11; calYear-- }
                viewModel.loadRideDaysForMonth(calYear, calMonth)
            }
        }
        val tvMonthLabel = TextView(ctx).apply {
            text = "${monthNames[calMonth]} $calYear"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorTextMain)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnNext = TextView(ctx).apply {
            text = "▶"; textSize = 16f; setPadding(16, 8, 16, 8)
            setTextColor(colorPrimary)
            setOnClickListener {
                calMonth++
                if (calMonth > 11) { calMonth = 0; calYear++ }
                viewModel.loadRideDaysForMonth(calYear, calMonth)
            }
        }
        headerRow.addView(btnPrev)
        headerRow.addView(tvMonthLabel)
        headerRow.addView(btnNext)
        container.addView(headerRow)

        // ── Day-of-week labels ────────────────────────────────────────────────
        val dowRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        arrayOf("Su","Mo","Tu","We","Th","Fr","Sa").forEach { d ->
            dowRow.addView(TextView(ctx).apply {
                text = d; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(colorTextSec)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        container.addView(dowRow)

        // ── Day cells ─────────────────────────────────────────────────────────
        val cal = Calendar.getInstance().apply {
            set(calYear, calMonth, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val firstDow   = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=Sun
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayCal = Calendar.getInstance()
        val todayYear  = todayCal.get(Calendar.YEAR)
        val todayMonth = todayCal.get(Calendar.MONTH)
        val todayDay   = todayCal.get(Calendar.DAY_OF_MONTH)

        var dayNum = 1
        var row: LinearLayout? = null

        for (cell in 0 until (firstDow + daysInMonth + 6) / 7 * 7) {
            if (cell % 7 == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                container.addView(row)
            }

            val cellView = TextView(ctx).apply {
                gravity   = Gravity.CENTER
                textSize  = 12f
                setPadding(2, 6, 2, 6)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            if (cell < firstDow || dayNum > daysInMonth) {
                // empty cell
            } else {
                val d = dayNum
                cellView.text = "$d"

                // Build epoch millis for this day's start
                val dayCal = Calendar.getInstance().apply {
                    set(calYear, calMonth, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                }
                val dayMillis = dayCal.timeInMillis

                val isToday = (calYear == todayYear && calMonth == todayMonth && d == todayDay)
                val isRideDay = rideDays.contains(dayMillis)

                when {
                    isRideDay -> {
                        // Filled red circle — tapping navigates to that day in History
                        cellView.setBackgroundResource(R.drawable.bg_calendar_dot)
                        cellView.background?.setTint(colorPrimary)
                        cellView.setTextColor(Color.WHITE)
                        cellView.setTypeface(null, Typeface.BOLD)
                        cellView.setOnClickListener {
                            sharedViewModel.setSelectedDate(dayMillis)
                        }
                    }
                    isToday -> {
                        // Orange ring for today (no data)
                        cellView.setBackgroundResource(R.drawable.bg_calendar_dot)
                        cellView.background?.setTint(colorToday)
                        cellView.setTextColor(Color.WHITE)
                    }
                    else -> {
                        cellView.setTextColor(colorTextSec)
                    }
                }
                dayNum++
            }
            row?.addView(cellView)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers unchanged from before
    // ─────────────────────────────────────────────────────────────────────────

    private fun showJsonInputDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = """Paste JSON list — each object = one separate trip:
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

    private fun addExtraPayRow(keyHint: String = "", amountHint: String = "") {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }
        val keyOptions = arrayOf(
            "customer_tip","surge_pay","incentive_pay",
            "rain_bonus","long_distance_pay","peak_pay","other"
        )
        val spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, keyOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            if (keyHint.isNotEmpty()) {
                val idx = keyOptions.indexOf(keyHint)
                if (idx >= 0) setSelection(idx)
            }
        }
        val etAmount = android.widget.EditText(ctx).apply {
            hint = "₹"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 14f
            if (amountHint.isNotEmpty()) setText(amountHint)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(ctx.getColor(R.color.text_primary))
            setHintTextColor(ctx.getColor(R.color.text_secondary))
        }
        val btnRemove = android.widget.Button(ctx).apply {
            text = "✕"; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { binding.containerExtraPays.removeView(row) }
            background = null
            setTextColor(ctx.getColor(R.color.negative))
        }
        row.addView(spinner); row.addView(etAmount); row.addView(btnRemove)
        binding.containerExtraPays.addView(row)
    }

    private fun collectExtraPays(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val container = binding.containerExtraPays
        for (i in 0 until container.childCount) {
            val row      = container.getChildAt(i) as? LinearLayout ?: continue
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
