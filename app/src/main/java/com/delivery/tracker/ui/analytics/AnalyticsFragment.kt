package com.delivery.tracker.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.databinding.FragmentAnalyticsBinding
import com.delivery.tracker.viewmodel.AnalyticsViewModel
import com.delivery.tracker.viewmodel.RestaurantStat
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChart()
        setupRestaurantList()
        setupObservers()
    }

    private fun setupChart() {
        binding.chartHours.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            xAxis.textColor = Color.parseColor("#B0B0B0")
            axisLeft.textColor = Color.parseColor("#B0B0B0")
            axisRight.isEnabled = false
            xAxis.granularity = 1f
        }
    }

    private fun setupRestaurantList() {
        binding.rvRestaurants.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupObservers() {
        viewModel.analyticsSummary.observe(viewLifecycleOwner) { summary ->
            binding.apply {
                tvTotalAnalyzed.text = "${summary.totalTripsAnalyzed}"
                tvTopRestaurant.text = summary.topRestaurant.ifEmpty { "—" }
                tvPeakHour.text = summary.peakHour.ifEmpty { "—" }
                tvAvgOrders.text = String.format("%.1f", summary.avgOrdersPerDay)
            }

            // Update bar chart
            val entries = summary.hourStats.map { hourStat ->
                BarEntry(hourStat.hour.toFloat(), hourStat.orderCount.toFloat())
            }
            val dataSet = BarDataSet(entries, "Orders").apply {
                color = Color.parseColor("#E23744")
                valueTextColor = Color.parseColor("#B0B0B0")
                valueTextSize = 9f
            }
            binding.chartHours.apply {
                data = BarData(dataSet)
                xAxis.valueFormatter = IndexAxisValueFormatter(
                    summary.hourStats.map { it.label }
                )
                invalidate()
            }

            // Update restaurant list
            binding.rvRestaurants.adapter =
                RestaurantAdapter(summary.restaurantStats)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
