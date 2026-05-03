package com.delivery.tracker.ui.expenses

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.delivery.tracker.data.model.TdsEntry
import com.delivery.tracker.databinding.FragmentExpensesBinding
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.ExpensesViewModel
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.content.Context
import android.content.SharedPreferences

@AndroidEntryPoint
class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpensesViewModel by viewModels()

    // Stores the week the user picked from the dialog
    private var selectedWeekLabel: String = ""
    private var selectedWeekStart: Long = 0L
    private var selectedWeekEnd: Long   = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabListener()
        setupObservers()
        setupListeners()
        showSection(0)

        // Pre-fill saved fuel price if available
        val prefs = requireContext().getSharedPreferences("fuel_prefs", Context.MODE_PRIVATE)
        val savedPrice = prefs.getString("last_fuel_price", "")
        if (!savedPrice.isNullOrEmpty()) {
            binding.etFuelPrice.setText(savedPrice)
        }
    }

    private fun setupTabListener() {
        binding.tabExpenseMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showSection(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showSection(index: Int) {
        binding.sectionFuel.visibility    = if (index == 0) View.VISIBLE else View.GONE
        binding.sectionService.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.sectionTds.visibility     = if (index == 2) View.VISIBLE else View.GONE
        binding.sectionCycle.visibility   = if (index == 3) View.VISIBLE else View.GONE
    }

    private fun setupObservers() {
        viewModel.cycleSummary.observe(viewLifecycleOwner) { summary ->
            val cycle = summary.cycle ?: return@observe
            binding.apply {
                tvCycleProgress.text =
                    "${FormatUtils.formatKm(cycle.kmCovered)} / ${FormatUtils.formatKm(cycle.cycleKmLimit)} " +
                    "(${cycle.progressPercent}%)"
                pbCycle.progress = cycle.progressPercent
                tvCycleEarnings.text = FormatUtils.formatMoney(summary.totalEarnings)
                tvCycleFuel.text     = FormatUtils.formatMoney(summary.totalFuelSpent)
                tvCycleService.text  = FormatUtils.formatMoney(summary.totalServiceSpent)

                tvCycleFuelBalance.text = FormatUtils.formatBalance(summary.fuelBalance)
                tvCycleFuelBalance.setTextColor(
                    requireContext().getColor(
                        if (summary.fuelBalance >= 0) com.delivery.tracker.R.color.positive
                        else com.delivery.tracker.R.color.negative
                    )
                )

                tvCycleServiceBalance.text = FormatUtils.formatBalance(summary.serviceBalance)
                tvCycleServiceBalance.setTextColor(
                    requireContext().getColor(
                        if (summary.serviceBalance >= 0) com.delivery.tracker.R.color.positive
                        else com.delivery.tracker.R.color.negative
                    )
                )
            }
        }

        viewModel.fuelSaved.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Fuel entry saved ✅", Toast.LENGTH_SHORT).show()
            clearFuelForm()
        }

        viewModel.serviceSaved.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Service entry saved ✅", Toast.LENGTH_SHORT).show()
            clearServiceForm()
        }

        viewModel.tdsSaved.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "TDS entry saved ✅", Toast.LENGTH_SHORT).show()
            clearTdsForm()
        }

        viewModel.allTds.observe(viewLifecycleOwner) { entries ->
            renderTdsList(entries)
        }
    }
    
    private fun renderTdsList(entries: List<TdsEntry>) {
        val container = binding.llTdsEntries
        val emptyView = binding.tvTdsEmpty
        val totalView = binding.tvTdsTotal

        container.removeAllViews()

        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            totalView.text = "Total: ₹0"
            return
        }

        emptyView.visibility = View.GONE
        val ctx = requireContext()
        var total = 0.0

        entries.forEach { entry ->
            total += entry.amount

            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(ctx.getColor(com.delivery.tracker.R.color.divider))
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }

            val weekTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = entry.weekLabel
                textSize = 13f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
            }

            val amtTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 16 }
                text = "₹${String.format("%.0f", entry.amount)}"
                textSize = 13f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.negative))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val deleteTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "🗑"
                textSize = 14f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("Delete TDS Entry")
                        .setMessage("Remove ₹${String.format("%.0f", entry.amount)} for ${entry.weekLabel}?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deleteTdsEntry(entry) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            row.addView(weekTv)
            row.addView(amtTv)
            row.addView(deleteTv)
            container.addView(divider)
            container.addView(row)
        }

        totalView.text = "Total: ₹${String.format("%.0f", total)}"
    }

    private fun setupListeners() {
        binding.btnSaveFuel.setOnClickListener {
            val odometer = binding.etFuelOdometer.text.toString().toDoubleOrNull()
            val price    = binding.etFuelPrice.text.toString().toDoubleOrNull()
            val amount   = binding.etFuelAmount.text.toString().toDoubleOrNull()
            if (odometer == null || price == null || amount == null) {
                Toast.makeText(requireContext(), "Fill all fuel fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Save this price so it pre-fills next time
            requireContext()
                .getSharedPreferences("fuel_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_fuel_price", price.toString())
                .apply()
            viewModel.addFuelEntry(odometer, price, amount)
        }


        binding.btnSaveService.setOnClickListener {
            val odometer = binding.etServiceOdometer.text.toString().toDoubleOrNull()
            val amount   = binding.etServiceAmount.text.toString().toDoubleOrNull()
            val details  = binding.etServiceDetails.text.toString().trim()
            if (odometer == null || amount == null || details.isEmpty()) {
                Toast.makeText(requireContext(), "Fill all service fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addServiceEntry(odometer, amount, details)
        }

        // ── TDS week selector ──────────────────────────────────────────────
        binding.etTdsWeek.setOnClickListener {
            showWeekPickerDialog()
        }

        binding.btnSaveTds.setOnClickListener {
            val amount = binding.etTdsAmount.text.toString().toDoubleOrNull()
            if (selectedWeekLabel.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a week first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amount == null || amount <= 0) {
                Toast.makeText(requireContext(), "Enter TDS amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addTdsEntry(
                weekLabel = selectedWeekLabel,
                weekStart = selectedWeekStart,
                weekEnd   = selectedWeekEnd,
                amount    = amount
            )
        }

        binding.btnNewCycle.setOnClickListener {
            showNewCycleDialog()
        }
    }

    // ── Week picker dialog ─────────────────────────────────────────────────
    private fun showWeekPickerDialog() {
        // Let user pick which month's weeks to browse (default = current month)
        val monthNames = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear  = now.get(Calendar.YEAR)

        // Build list of "Month Year" options — last 6 months up to current
        data class MonthOption(val label: String, val month: Int, val year: Int)
        val monthOptions = mutableListOf<MonthOption>()
        val cal = Calendar.getInstance()
        repeat(6) {
            monthOptions.add(0, MonthOption(
                label = "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}",
                month = cal.get(Calendar.MONTH),
                year  = cal.get(Calendar.YEAR)
            ))
            cal.add(Calendar.MONTH, -1)
        }

        val monthLabels = monthOptions.map { it.label }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select month")
            .setItems(monthLabels) { _, monthIdx ->
                val chosen = monthOptions[monthIdx]
                showWeekListForMonth(chosen.month, chosen.year)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWeekListForMonth(month: Int, year: Int) {
        val weeks = getWeeksOfMonth(month, year)
        val labels = weeks.map { it.first }.toTypedArray()

        // Pre-select the week containing today if browsing current month
        val now = Calendar.getInstance()
        val defaultIdx = if (now.get(Calendar.MONTH) == month && now.get(Calendar.YEAR) == year) {
            val todayWeekStart = DateUtils.startOfWeekInMonth(now.timeInMillis)
            weeks.indexOfFirst { it.second == todayWeekStart }.coerceAtLeast(0)
        } else 0

        AlertDialog.Builder(requireContext())
            .setTitle("Select week")
            .setSingleChoiceItems(labels, defaultIdx) { dialog, which ->
                selectedWeekLabel = weeks[which].first
                selectedWeekStart = weeks[which].second
                selectedWeekEnd   = DateUtils.endOfWeekInMonth(weeks[which].second)
                // Update the TextView to show what was selected
                binding.etTdsWeek.text = selectedWeekLabel
                binding.etTdsWeek.setTextColor(
                    requireContext().getColor(com.delivery.tracker.R.color.text_primary)
                )
                dialog.dismiss()
            }
            .setNegativeButton("Back") { _, _ ->
                // Go back to month picker
                showWeekPickerDialog()
            }
            .show()
    }

    /**
     * Returns all Mon–Sun week ranges inside the given month,
     * each as Triple(displayLabel, weekStartMillis).
     */
    private fun getWeeksOfMonth(month: Int, year: Int): List<Pair<String, Long>> {
        val dayCal = Calendar.getInstance().apply {
            set(year, month, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val fmt   = SimpleDateFormat("MMM", Locale.getDefault())
        val weeks = mutableListOf<Pair<String, Long>>()

        while (dayCal.get(Calendar.MONTH) == month) {
            val weekStart = DateUtils.startOfWeekInMonth(dayCal.timeInMillis)
            val weekEnd   = DateUtils.endOfWeekInMonth(dayCal.timeInMillis)

            val startDay  = Calendar.getInstance().apply { timeInMillis = weekStart }
                .get(Calendar.DAY_OF_MONTH)
            val endDay    = Calendar.getInstance().apply { timeInMillis = weekEnd }
                .get(Calendar.DAY_OF_MONTH)
            val monthName = fmt.format(dayCal.time)
            val label     = "$startDay–$endDay $monthName"

            if (weeks.none { it.second == weekStart }) {
                weeks.add(Pair(label, weekStart))
            }
            dayCal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return weeks
    }

    private fun showNewCycleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Start New Service Cycle")
            .setMessage("Enter starting odometer, fuel budget and service budget to begin a new 3000km cycle.")
            .setPositiveButton("Start") { _, _ ->
                viewModel.startNewCycle(0.0, 3000.0, 1500.0)
                Toast.makeText(requireContext(), "New cycle started 🔄", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearFuelForm() {
        binding.etFuelOdometer.text?.clear()
        binding.etFuelAmount.text?.clear()
    }

    private fun clearServiceForm() {
        binding.etServiceOdometer.text?.clear()
        binding.etServiceAmount.text?.clear()
        binding.etServiceDetails.text?.clear()
    }

    private fun clearTdsForm() {
        // Reset week picker back to placeholder
        selectedWeekLabel = ""
        selectedWeekStart = 0L
        selectedWeekEnd   = 0L
        binding.etTdsWeek.text = "Tap to select week ▾"
        binding.etTdsWeek.setTextColor(
            requireContext().getColor(com.delivery.tracker.R.color.text_secondary)
        )
        binding.etTdsAmount.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}