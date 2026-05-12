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
import com.delivery.tracker.data.model.FuelEntry
import com.delivery.tracker.data.model.ServiceEntry
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
import androidx.lifecycle.lifecycleScope
import com.delivery.tracker.viewmodel.CycleSummary
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpensesViewModel by viewModels()
    private var currentTabIndex = 0   // tracks active inner tab: 0=Fuel,1=Service,2=TDS,3=Cycle
    private var selectedFuelDateMillis    = System.currentTimeMillis()
    private var selectedServiceDateMillis = System.currentTimeMillis()

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

        // Show last known odometer as hint so users don't enter wrong values
        viewModel.getLastKnownOdometer { lastOdo ->
            if (lastOdo > 0) {
                binding.tilFuelOdometer.hint    = "Odometer Reading (km)  •  Last: ${String.format("%.1f", lastOdo)} km"
                binding.tilServiceOdometer.hint = "Odometer Reading (km)  •  Last: ${String.format("%.1f", lastOdo)} km"
            }
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
        currentTabIndex = index
        binding.sectionFuel.visibility    = if (index == 0) View.VISIBLE else View.GONE
        binding.sectionService.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.sectionTds.visibility     = if (index == 2) View.VISIBLE else View.GONE
        binding.sectionCycle.visibility   = if (index == 3) View.VISIBLE else View.GONE
    }

    private fun setupObservers() {
        viewModel.cycleSummary.observe(viewLifecycleOwner) { summary ->
            val cycle = summary.cycle ?: return@observe
            val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            binding.apply {
                // Use kmRidden from summary (session-based for active cycle, odo-diff for ended)
                tvCycleProgress.text =
                    "Started: ${String.format(\"%.1f\", cycle.startOdometer)} km  |  " +
                    "Run: ${String.format(\"%.1f\", summary.kmRidden)} km  |  " +
                    "From: ${fmt.format(java.util.Date(cycle.startDateMillis))}"
                pbCycle.visibility = View.GONE

                // Allocated column — km × rate per km (money saved from riding)
                tvCycleFuelAllocated.text    = FormatUtils.formatMoney(summary.fuelAllocated)
                tvCycleServiceAllocated.text = FormatUtils.formatMoney(summary.serviceAllocated)

                // Used column — actual money spent in Expenses tab
                tvCycleFuelUsed.text    = FormatUtils.formatMoney(summary.fuelUsed)
                tvCycleServiceUsed.text = FormatUtils.formatMoney(summary.serviceUsed)

                // Remaining column — allocated minus used
                tvCycleFuelRemaining.text = FormatUtils.formatBalance(summary.fuelRemaining)
                tvCycleFuelRemaining.setTextColor(
                    requireContext().getColor(
                        if (summary.fuelRemaining >= 0) com.delivery.tracker.R.color.positive
                        else com.delivery.tracker.R.color.negative
                    )
                )
                tvCycleServiceRemaining.text = FormatUtils.formatBalance(summary.serviceRemaining)
                tvCycleServiceRemaining.setTextColor(
                    requireContext().getColor(
                        if (summary.serviceRemaining >= 0) com.delivery.tracker.R.color.positive
                        else com.delivery.tracker.R.color.negative
                    )
                )

                // Earnings row = Net Remaining after all deductions
                // (base pay + extras) − fuel spent − service spent − TDS
                tvCycleEarnings.text = FormatUtils.formatBalance(summary.netRemaining)
                tvCycleEarnings.setTextColor(
                    requireContext().getColor(
                        if (summary.netRemaining >= 0) com.delivery.tracker.R.color.positive
                        else com.delivery.tracker.R.color.negative
                    )
                )

                // Show End Cycle button only if cycle is active
                btnEndCycle.visibility = if (cycle.isActive) View.VISIBLE else View.GONE
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

        viewModel.allFuel.observe(viewLifecycleOwner) { entries ->
            renderFuelList(entries)
        }

        viewModel.allService.observe(viewLifecycleOwner) { entries ->
            renderServiceList(entries)
        }

        viewModel.allCycles.observe(viewLifecycleOwner) { cycles ->
            renderCyclesList(cycles)
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

    private fun renderFuelList(entries: List<FuelEntry>) {
        val container = binding.llFuelEntries
        val emptyView = binding.tvFuelEmpty
        val totalView = binding.tvFuelTotal
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
            total += entry.amountSpent
            val fmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())

            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(ctx.getColor(com.delivery.tracker.R.color.divider))
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }
            val infoTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${fmt.format(java.util.Date(entry.dateMillis))}  •  ${String.format(\"%.1f\", entry.odometerReading)} km  •  ₹${entry.fuelPricePerLitre}/L"
                textSize = 12f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
            }
            val amtTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 16 }
                text = "₹${String.format("%.0f", entry.amountSpent)}"
                textSize = 13f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.negative))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val deleteTv = TextView(ctx).apply {
                text = "🗑"
                textSize = 14f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("Delete Fuel Entry")
                        .setMessage("Remove ₹${String.format("%.0f", entry.amountSpent)} fuel entry?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deleteFuelEntry(entry) }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            val editTv = TextView(ctx).apply {
                text = "✏️"
                textSize = 14f
                setPadding(0, 0, 16, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { showEditFuelDialog(entry) }
            }
            row.addView(infoTv)
            row.addView(amtTv)
            row.addView(editTv)
            row.addView(deleteTv)
            container.addView(divider)
            container.addView(row)
        }
        totalView.text = "Total: ₹${String.format("%.0f", total)}"
    }

    private fun renderServiceList(entries: List<ServiceEntry>) {
        val container = binding.llServiceEntries
        val emptyView = binding.tvServiceEmpty
        val totalView = binding.tvServiceTotal
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
            total += entry.amountSpent
            val fmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())

            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(ctx.getColor(com.delivery.tracker.R.color.divider))
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }
            val infoTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${fmt.format(java.util.Date(entry.dateMillis))}  •  ${String.format(\"%.1f\", entry.odometerReading)} km  •  ${entry.details}"
                textSize = 12f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
            }
            val amtTv = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 16 }
                text = "₹${String.format("%.0f", entry.amountSpent)}"
                textSize = 13f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.negative))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val deleteTv = TextView(ctx).apply {
                text = "🗑"
                textSize = 14f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("Delete Service Entry")
                        .setMessage("Remove ₹${String.format("%.0f", entry.amountSpent)} service entry?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deleteServiceEntry(entry) }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            val editTv = TextView(ctx).apply {
                text = "✏️"
                textSize = 14f
                setPadding(0, 0, 16, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { showEditServiceDialog(entry) }
            }
            row.addView(infoTv)
            row.addView(amtTv)
            row.addView(editTv)
            row.addView(deleteTv)
            container.addView(divider)
            container.addView(row)
        }
        totalView.text = "Total: ₹${String.format("%.0f", total)}"
    }

    private fun setupListeners() {
        // ── Fuel date picker ────────────────────────────────────────────
        updateFuelDateLabel()
        binding.btnFuelPickDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = selectedFuelDateMillis
            }
            android.app.DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedFuelDateMillis = cal.timeInMillis
                    updateFuelDateLabel()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

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
            // Issue 5: Pass the user-selected date, not System.currentTimeMillis()
            viewModel.addFuelEntry(odometer, price, amount, selectedFuelDateMillis)
        }


        // ── Service date picker ─────────────────────────────────────────
        updateServiceDateLabel()
        binding.btnServicePickDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = selectedServiceDateMillis
            }
            android.app.DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedServiceDateMillis = cal.timeInMillis
                    updateServiceDateLabel()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnSaveService.setOnClickListener {
            val odometer = binding.etServiceOdometer.text.toString().toDoubleOrNull()
            val amount   = binding.etServiceAmount.text.toString().toDoubleOrNull()
            val details  = binding.etServiceDetails.text.toString().trim()
            if (odometer == null || amount == null || details.isEmpty()) {
                Toast.makeText(requireContext(), "Fill all service fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Issue 5: Pass the user-selected date, not System.currentTimeMillis()
            viewModel.addServiceEntry(odometer, amount, details, selectedServiceDateMillis)
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

        binding.btnEndCycle.setOnClickListener {
            showEndCycleDialog()
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
        val weeks = DateUtils.weeksOverlappingMonth(year, month)   // ← was (month, year) — WRONG
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
                selectedWeekEnd = DateUtils.endOfWeek(weeks[which].second)
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

   

    private fun showNewCycleDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etStartOdo = android.widget.EditText(ctx).apply {
            hint = "Start Odometer reading (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(etStartOdo)

        viewModel.getLastKnownOdometer { lastOdo ->
            if (lastOdo > 0) etStartOdo.setText(String.format("%.1f", lastOdo))
        }


        // Date picker button — defaults to today
        val selectedCal = Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val tvDate = TextView(ctx).apply {
            text = "📅 Start Date: ${fmt.format(selectedCal.time)}"
            textSize = 14f
            setPadding(0, 16, 0, 4)
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_primary))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                android.app.DatePickerDialog(
                    ctx,
                    { _, y, m, d ->
                        selectedCal.set(y, m, d, 0, 0, 0)
                        selectedCal.set(Calendar.MILLISECOND, 0)
                        text = "📅 Start Date: ${fmt.format(selectedCal.time)}"
                    },
                    selectedCal.get(Calendar.YEAR),
                    selectedCal.get(Calendar.MONTH),
                    selectedCal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        layout.addView(tvDate)

        AlertDialog.Builder(ctx)
            .setTitle("🔄 Start New Service Cycle")
            .setMessage("Enter odometer reading and start date.")
            .setView(layout)
            .setPositiveButton("Start") { _, _ ->
                val startOdo = etStartOdo.text.toString().toDoubleOrNull()
                if (startOdo == null || startOdo <= 0) {
                    Toast.makeText(ctx, "Enter a valid odometer reading", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.startNewCycleWithDate(startOdo, selectedCal.timeInMillis)
                Toast.makeText(ctx, "New cycle started 🔄", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEndCycleDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etEndOdo = android.widget.EditText(ctx).apply {
            hint = "Current odometer reading (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val cbStartNew = android.widget.CheckBox(ctx).apply {
            text = "Start new cycle from this odometer reading"
            isChecked = true
            setPadding(0, 16, 0, 0)
        }

        layout.addView(etEndOdo)
        layout.addView(cbStartNew)

        AlertDialog.Builder(ctx)
            .setTitle("🏁 End Service Cycle")
            .setMessage("Enter your current odometer to close this cycle.")
            .setView(layout)
            .setPositiveButton("End Cycle") { _, _ ->
                val endOdo = etEndOdo.text.toString().toDoubleOrNull()
                if (endOdo == null || endOdo <= 0) {
                    Toast.makeText(ctx, "Enter a valid odometer reading", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (cbStartNew.isChecked) {
                    // End current cycle AND immediately start next one at same odo
                    viewModel.endAndStartNewCycle(endOdo)
                    Toast.makeText(ctx, "Cycle ended & new cycle started ✅", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.endCurrentCycle(endOdo)
                    Toast.makeText(ctx, "Cycle ended ✅", Toast.LENGTH_SHORT).show()
                }
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

    // ── Cycles list ───────────────────────────────────────────────────────
    private fun renderCyclesList(cycles: List<com.delivery.tracker.data.model.ServiceCycle>) {
        val container = binding.llCyclesList
        val emptyView = binding.tvCyclesEmpty
        container.removeAllViews()

        if (cycles.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        val ctx = requireContext()
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        cycles.forEach { cycle ->
            val divider = View(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(ctx.getColor(com.delivery.tracker.R.color.divider))
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val statusBadge = if (cycle.isActive) " 🟢 Active" else " ⚫ Ended"
            val endText = if (cycle.endOdometer > 0) " → ${String.format("%.1f", cycle.endOdometer)} km" else ""

            val infoRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val infoTv = TextView(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 13f
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_primary))
                text = "${fmt.format(java.util.Date(cycle.startDateMillis))}$statusBadge\n" +
                    "Odo: ${String.format(\"%.1f\", cycle.startOdometer)}$endText km  |  Run: ${String.format(\"%.1f\", cycle.kmCovered)} km"
            }

            // Fetch live details per cycle and add a second detail row
            val detailTv = TextView(ctx).apply {
                textSize = 12f
                setPadding(0, 2, 0, 0)
                setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_secondary))
                text = "Loading..."
            }

            // Async fetch earnings + fuel/service per cycle
            viewLifecycleOwner.lifecycleScope.launch {
                val earnings    = viewModel.getCycleEarnings(cycle.id)
                val extras      = viewModel.getCycleExtras(cycle.id)
                val fuelUsed    = viewModel.getCycleFuelUsed(cycle.id)
                val serviceUsed = viewModel.getCycleServiceUsed(cycle.id)
                // Use session-based km for active cycle; odo-diff for ended
                val kmForCalc   = if (cycle.isActive) viewModel.getCycleKmRidden(cycle.id)
                                  else cycle.kmCovered
                val fuelAlloc   = kmForCalc * CycleSummary.FUEL_RATE_PER_KM
                val svcAlloc    = kmForCalc * CycleSummary.SERVICE_RATE_PER_KM
                val fuelRem     = fuelAlloc - fuelUsed
                val svcRem      = svcAlloc - serviceUsed
                val netRem      = (earnings + extras) - fuelUsed - serviceUsed
                val fuelSign    = if (fuelRem >= 0.0) "✅" else "⚠️"
                val svcSign     = if (svcRem >= 0.0) "✅" else "⚠️"
                val netSign     = if (netRem >= 0.0) "✅" else "⚠️"
                detailTv.text =
                    "Net remaining: $netSign ₹${String.format("%.0f", netRem)}  |  " +
                    "Fuel rem: $fuelSign ₹${String.format("%.0f", fuelRem)}  |  " +
                    "Svc rem: $svcSign ₹${String.format("%.0f", svcRem)}"
            }

            val editTv = TextView(ctx).apply {
                text = "✏️"
                textSize = 18f
                setPadding(16, 0, 8, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { showEditCycleDialog(cycle) }
            }

            val deleteTv = TextView(ctx).apply {
                text = "🗑"
                textSize = 18f
                setPadding(8, 0, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("Delete Cycle")
                        .setMessage("Delete cycle starting ${fmt.format(java.util.Date(cycle.startDateMillis))}?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deleteCycle(cycle) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            infoRow.addView(infoTv)
            infoRow.addView(editTv)
            infoRow.addView(deleteTv)
            row.addView(infoRow)
            row.addView(detailTv)
            container.addView(divider)
            container.addView(row)
        }
    }

    private fun showEditCycleDialog(cycle: com.delivery.tracker.data.model.ServiceCycle) {
        val ctx = requireContext()
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val etStartOdo = android.widget.EditText(ctx).apply {
            hint = "Start Odometer (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.1f", cycle.startOdometer))
        }
        layout.addView(etStartOdo)

        val etEndOdo = android.widget.EditText(ctx).apply {
            hint = "End Odometer (km) — leave 0 if still active"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (cycle.endOdometer > 0) String.format("%.1f", cycle.endOdometer) else "")
        }
        layout.addView(etEndOdo)

        // Start date picker
        val startCal = Calendar.getInstance().apply { timeInMillis = cycle.startDateMillis }
        val tvStartDate = TextView(ctx).apply {
            text = "📅 Start Date: ${fmt.format(java.util.Date(cycle.startDateMillis))}"
            textSize = 14f
            setPadding(0, 16, 0, 4)
            setTextColor(ctx.getColor(com.delivery.tracker.R.color.text_primary))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                android.app.DatePickerDialog(
                    ctx,
                    { _, y, m, d ->
                        startCal.set(y, m, d, 0, 0, 0)
                        startCal.set(Calendar.MILLISECOND, 0)
                        text = "📅 Start Date: ${fmt.format(startCal.time)}"
                    },
                    startCal.get(Calendar.YEAR),
                    startCal.get(Calendar.MONTH),
                    startCal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        layout.addView(tvStartDate)

        AlertDialog.Builder(ctx)
            .setTitle("✏️ Edit Cycle")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newStartOdo = etStartOdo.text.toString().toDoubleOrNull() ?: cycle.startOdometer
                val newEndOdo   = etEndOdo.text.toString().toDoubleOrNull() ?: cycle.endOdometer
                val isStillActive = newEndOdo <= 0.0
                viewModel.updateCycleDetails(
                    cycle.copy(
                        startOdometer   = newStartOdo,
                        endOdometer     = if (isStillActive) 0.0 else newEndOdo,
                        startDateMillis = startCal.timeInMillis,
                        isActive        = isStillActive
                    )
                )
                Toast.makeText(ctx, "Cycle updated ✅", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun showEditFuelDialog(entry: FuelEntry) {
        val ctx = requireContext()
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        var editDateMillis = entry.dateMillis
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val btnDate = android.widget.Button(ctx).apply {
            text = "📅 ${fmt.format(java.util.Date(editDateMillis))}"
        }
        btnDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = editDateMillis }
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                cal.set(y, m, d)
                editDateMillis = cal.timeInMillis
                btnDate.text = "📅 ${fmt.format(java.util.Date(editDateMillis))}"
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
               cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val etOdo = android.widget.EditText(ctx).apply {
            hint = "Odometer (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.1f", entry.odometerReading))
        }
        val etPrice = android.widget.EditText(ctx).apply {
            hint = "Price per Litre (₹)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.fuelPricePerLitre.toString())
        }
        val etAmount = android.widget.EditText(ctx).apply {
            hint = "Amount Spent (₹)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.amountSpent.toInt().toString())
        }
        layout.addView(btnDate)
        layout.addView(etOdo)
        layout.addView(etPrice)
        layout.addView(etAmount)

        AlertDialog.Builder(ctx)
            .setTitle("✏️ Edit Fuel Entry")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val odo    = etOdo.text.toString().toDoubleOrNull() ?: entry.odometerReading
                val price  = etPrice.text.toString().toDoubleOrNull() ?: entry.fuelPricePerLitre
                val amount = etAmount.text.toString().toDoubleOrNull() ?: entry.amountSpent
                viewModel.updateFuelEntry(entry.copy(
                    dateMillis        = editDateMillis,
                    odometerReading   = odo,
                    fuelPricePerLitre = price,
                    amountSpent       = amount
                ))
                Toast.makeText(ctx, "Fuel entry updated ✅", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditServiceDialog(entry: ServiceEntry) {
        val ctx = requireContext()
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        var editDateMillis = entry.dateMillis
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val btnDate = android.widget.Button(ctx).apply {
            text = "📅 ${fmt.format(java.util.Date(editDateMillis))}"
        }
        btnDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = editDateMillis }
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                cal.set(y, m, d)
                editDateMillis = cal.timeInMillis
                btnDate.text = "📅 ${fmt.format(java.util.Date(editDateMillis))}"
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
               cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val etOdo = android.widget.EditText(ctx).apply {
            hint = "Odometer (km)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.1f", entry.odometerReading))
        }
        val etAmount = android.widget.EditText(ctx).apply {
            hint = "Amount Spent (₹)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.amountSpent.toInt().toString())
        }
        val etDetails = android.widget.EditText(ctx).apply {
            hint = "Details (e.g. Oil change)"
            setText(entry.details)
        }
        layout.addView(btnDate)
        layout.addView(etOdo)
        layout.addView(etAmount)
        layout.addView(etDetails)

        AlertDialog.Builder(ctx)
            .setTitle("✏️ Edit Service Entry")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val odo     = etOdo.text.toString().toDoubleOrNull() ?: entry.odometerReading
                val amount  = etAmount.text.toString().toDoubleOrNull() ?: entry.amountSpent
                val details = etDetails.text.toString().trim().ifEmpty { entry.details }
                viewModel.updateServiceEntry(entry.copy(
                    dateMillis      = editDateMillis,
                    odometerReading = odo,
                    amountSpent     = amount,
                    details         = details
                ))
                Toast.makeText(ctx, "Service entry updated ✅", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Called by MainActivity when a horizontal swipe is detected.
     * Moves through Fuel→Service→TDS→Cycle (direction=+1) or reverse (direction=-1).
     * Returns true if moved, false if already at boundary.
     */
    fun swipeInnerTab(direction: Int): Boolean {
        val tab     = binding.tabExpenseMode
        val target  = currentTabIndex + direction
        if (target < 0 || target >= tab.tabCount) return false
        tab.getTabAt(target)?.select()
        // showSection is called automatically via the tab listener
        return true
    }

    private fun updateFuelDateLabel() {
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        binding.btnFuelPickDate.text = "📅 ${fmt.format(java.util.Date(selectedFuelDateMillis))}"
    }

    private fun updateServiceDateLabel() {
        val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        binding.btnServicePickDate.text = "📅 ${fmt.format(java.util.Date(selectedServiceDateMillis))}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}