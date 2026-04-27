package com.delivery.tracker.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.databinding.FragmentHistoryBinding
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
        tripAdapter = TripAdapter { }  // no delete in history
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

                // Net remaining with color
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
    }

    private fun shiftDate(millis: Long, direction: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        when (viewModel.viewMode.value) {
            HistoryViewMode.DAY -> cal.add(Calendar.DAY_OF_MONTH, direction)
            HistoryViewMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, direction)
            HistoryViewMode.MONTH -> cal.add(Calendar.MONTH, direction)
            else -> cal.add(Calendar.DAY_OF_MONTH, direction)
        }
        return cal.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
