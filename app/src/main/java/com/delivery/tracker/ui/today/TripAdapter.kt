package com.delivery.tracker.ui.today

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.databinding.ItemTripBinding
import com.delivery.tracker.utils.FormatUtils

class TripAdapter(
    private val onDelete: (Trip) -> Unit
) : ListAdapter<Trip, TripAdapter.TripViewHolder>(TripDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TripViewHolder(
        private val binding: ItemTripBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(trip: Trip) {
            binding.apply {
                tvRestaurant.text = "🍽 ${trip.restaurantName.ifEmpty { "Unknown" }}"
                tvTime.text = trip.assignedTime
                tvOrderPay.text = FormatUtils.formatMoney(trip.orderPay)
                tvDistance.text = FormatUtils.formatKm(trip.screenshotDistance)

                // ₹/km for this trip
                val rate = trip.ratePerKmLive
                tvRatePerKm.text = if (rate > 0)
                    "📍 ${FormatUtils.formatRate(rate)}"
                else "📍 ₹/km: —"

                // Extras badge
                if (trip.totalExtras > 0) {
                    tvExtrasBadge.visibility = android.view.View.VISIBLE
                    tvExtrasBadge.text = "+${FormatUtils.formatMoney(trip.totalExtras)} extra"
                } else {
                    tvExtrasBadge.visibility = android.view.View.GONE
                }

                btnDeleteTrip.setOnClickListener { onDelete(trip) }
            }
        }
    }

    class TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Trip, newItem: Trip) = oldItem == newItem
    }
}
