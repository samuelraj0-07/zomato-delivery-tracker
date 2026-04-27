package com.delivery.tracker.ui.today

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.databinding.FragmentTodayBinding
import com.delivery.tracker.ocr.ZomatoOcrParser
import com.delivery.tracker.utils.DateUtils
import com.delivery.tracker.utils.FormatUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.delivery.tracker.viewmodel.TodayViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import java.io.InputStream

@AndroidEntryPoint
class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TodayViewModel by viewModels()
    private lateinit var tripAdapter: TripAdapter

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> runOcr(uri) }
        }
    }

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
        tripAdapter = TripAdapter { trip ->
            viewModel.deleteTrip(trip)
        }
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
                tvBaseEarnings.text = FormatUtils.formatMoney(summary.totalOrderPay)
                tvExtras.text = FormatUtils.formatMoney(summary.totalExtras)
                tvRateScreenshot.text = FormatUtils.formatRate(summary.ratePerKmLive)
                tvTotalDistance.text = FormatUtils.formatKm(summary.totalScreenshotDistance)
                tvTotalTrips.text = "${summary.totalTrips}"

                // Show actual rate only after day ended
                if (summary.isSessionEnded && summary.actualDistance > 0) {
                    rowActualRate.visibility = View.VISIBLE
                    rowDeadKm.visibility = View.VISIBLE
                    tvRateActual.text = FormatUtils.formatRate(summary.ratePerKmActual)
                    tvDeadKm.text = FormatUtils.formatKm(summary.deadKm)
                    btnEndDay.isEnabled = false
                    btnStartDay.isEnabled = false
                } else {
                    rowActualRate.visibility = View.GONE
                    rowDeadKm.visibility = View.GONE
                }
            }
        }

        viewModel.activeSession.observe(viewLifecycleOwner) { session ->
            binding.apply {
                if (session != null) {
                    etStartOdometer.setText(session.startOdometer.toString())
                    etStartOdometer.isEnabled = false
                    btnStartDay.isEnabled = false
                    btnEndDay.isEnabled = !session.isEnded
                } else {
                    etStartOdometer.isEnabled = true
                    btnStartDay.isEnabled = true
                    btnEndDay.isEnabled = false
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
        // Live ₹/km preview while typing
        val updatePreview = {
            val pay = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val dist = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0
            binding.tvRatePreview.text = if (pay > 0 && dist > 0)
                "₹/km: ${FormatUtils.formatRate(pay / dist)}"
            else "₹/km: —"
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
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            pickImageLauncher.launch(intent)
        }

        binding.btnAddTrip.setOnClickListener {
            val restaurant = binding.etRestaurant.text.toString().trim()
            val time = binding.etAssignedTime.text.toString().trim()
            val orderPay = binding.etOrderPay.text.toString().toDoubleOrNull() ?: 0.0
            val distance = binding.etDistance.text.toString().toDoubleOrNull() ?: 0.0
            val tips = binding.etTips.text.toString().toDoubleOrNull() ?: 0.0
            val surge = binding.etSurge.text.toString().toDoubleOrNull() ?: 0.0
            val incentive = binding.etIncentive.text.toString().toDoubleOrNull() ?: 0.0

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
                    sessionId = 0L, // ViewModel fills this
                    restaurantName = restaurant,
                    assignedTime = time,
                    orderPay = orderPay,
                    screenshotDistance = distance,
                    tips = tips,
                    surgePay = surge,
                    incentivePay = incentive,
                    dateMillis = System.currentTimeMillis()
                )
            )
            clearForm()
            Toast.makeText(requireContext(), "Trip added ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runOcr(uri: Uri) {
        Toast.makeText(requireContext(), "Scanning with AI... ⏳", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val bitmap: Bitmap = BitmapFactory.decodeStream(stream)

                // Try Gemini first, fallback to ML Kit regex
                var result = ZomatoOcrParser.parseWithGemini(bitmap)

                // Fallback: if Gemini returns empty, use ML Kit + regex
                if (result.restaurantName.isEmpty() && result.orderPay == 0.0) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val visionText = Tasks.await(recognizer.process(image))
                    result = ZomatoOcrParser.parseWithRegex(visionText.text)
                }

                withContext(Dispatchers.Main) {
                    binding.apply {
                        if (result.restaurantName.isNotEmpty())
                            etRestaurant.setText(result.restaurantName)
                        if (result.assignedTime.isNotEmpty())
                            etAssignedTime.setText(result.assignedTime)
                        if (result.orderPay > 0)
                            etOrderPay.setText(result.orderPay.toString())
                        if (result.distance > 0)
                            etDistance.setText(result.distance.toString())
                        if (result.tips > 0)
                            etTips.setText(result.tips.toString())
                        if (result.surgePay > 0)
                            etSurge.setText(result.surgePay.toString())
                        if (result.incentivePay > 0)
                            etIncentive.setText(result.incentivePay.toString())
                    }
                    Toast.makeText(requireContext(),
                        "AI scanned ✅ Check and edit if needed",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "Scan failed. Fill manually.", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
