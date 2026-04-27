package com.delivery.tracker.ui.expenses

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.delivery.tracker.databinding.FragmentExpensesBinding
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.ExpensesViewModel
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpensesViewModel by viewModels()

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
    }

    private fun setupTabListener() {
        binding.tabExpenseMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showSection(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showSection(index: Int) {
        binding.sectionFuel.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.sectionService.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.sectionTds.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.sectionCycle.visibility = if (index == 3) View.VISIBLE else View.GONE
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
                tvCycleFuel.text = FormatUtils.formatMoney(summary.totalFuelSpent)
                tvCycleService.text = FormatUtils.formatMoney(summary.totalServiceSpent)

                // Fuel balance color
                tvCycleFuelBalance.text = FormatUtils.formatBalance(summary.fuelBalance)
                tvCycleFuelBalance.setTextColor(
                    requireContext().getColor(
                        if (summary.fuelBalance >= 0)
                            com.delivery.tracker.R.color.positive
                        else com.delivery.tracker.R.color.negative
                    )
                )

                // Service balance color
                tvCycleServiceBalance.text = FormatUtils.formatBalance(summary.serviceBalance)
                tvCycleServiceBalance.setTextColor(
                    requireContext().getColor(
                        if (summary.serviceBalance >= 0)
                            com.delivery.tracker.R.color.positive
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
    }

    private fun setupListeners() {
        binding.btnSaveFuel.setOnClickListener {
            val odometer = binding.etFuelOdometer.text.toString().toDoubleOrNull()
            val price = binding.etFuelPrice.text.toString().toDoubleOrNull()
            val amount = binding.etFuelAmount.text.toString().toDoubleOrNull()
            if (odometer == null || price == null || amount == null) {
                Toast.makeText(requireContext(), "Fill all fuel fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addFuelEntry(odometer, price, amount)
        }

        binding.btnSaveService.setOnClickListener {
            val odometer = binding.etServiceOdometer.text.toString().toDoubleOrNull()
            val amount = binding.etServiceAmount.text.toString().toDoubleOrNull()
            val details = binding.etServiceDetails.text.toString().trim()
            if (odometer == null || amount == null || details.isEmpty()) {
                Toast.makeText(requireContext(), "Fill all service fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addServiceEntry(odometer, amount, details)
        }

        binding.btnSaveTds.setOnClickListener {
            val weekLabel = binding.etTdsWeek.text.toString().trim()
            val amount = binding.etTdsAmount.text.toString().toDoubleOrNull()
            if (weekLabel.isEmpty() || amount == null) {
                Toast.makeText(requireContext(), "Fill all TDS fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            viewModel.addTdsEntry(
                weekLabel = weekLabel,
                weekStart = DateUtils.startOfWeekInMonth(now),
                weekEnd = DateUtils.endOfWeekInMonth(now),
                amount = amount
            )
        }

        binding.btnNewCycle.setOnClickListener {
            showNewCycleDialog()
        }
    }

    private fun showNewCycleDialog() {
        val dialogView = layoutInflater.inflate(
            android.R.layout.simple_list_item_1, null
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Start New Service Cycle")
            .setMessage("Enter starting odometer, fuel budget and service budget to begin a new 3000km cycle.")
            .setPositiveButton("Start") { _, _ ->
                // simplified — in production show a proper input dialog
                viewModel.startNewCycle(0.0, 3000.0, 1500.0)
                Toast.makeText(requireContext(), "New cycle started 🔄", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearFuelForm() {
        binding.etFuelOdometer.text?.clear()
        binding.etFuelPrice.text?.clear()
        binding.etFuelAmount.text?.clear()
    }

    private fun clearServiceForm() {
        binding.etServiceOdometer.text?.clear()
        binding.etServiceAmount.text?.clear()
        binding.etServiceDetails.text?.clear()
    }

    private fun clearTdsForm() {
        binding.etTdsWeek.text?.clear()
        binding.etTdsAmount.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
