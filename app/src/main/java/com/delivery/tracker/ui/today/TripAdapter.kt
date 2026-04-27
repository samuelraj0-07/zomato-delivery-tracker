package com.delivery.tracker.ui.today

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.delivery.tracker.R
import com.delivery.tracker.data.model.SubOrder
import com.delivery.tracker.data.model.Trip
import com.delivery.tracker.databinding.ItemTripBinding
import com.delivery.tracker.utils.FormatUtils

class TripAdapter(
    private val onDelete: (Trip) -> Unit,
    private val getSubOrders: (Long, (List<SubOrder>) -> Unit) -> Unit
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

        private var isExpanded = false

        fun bind(trip: Trip) {
            binding.apply {
                tvRestaurant.text = "🍽 ${trip.restaurantName.ifEmpty { "Unknown" }}"
                tvTime.text = trip.assignedTime
                tvOrderPay.text = FormatUtils.formatMoney(trip.orderPay)
                tvDistance.text = FormatUtils.formatKm(trip.screenshotDistance)

                val rate = trip.ratePerKmLive
                tvRatePerKm.text = if (rate > 0)
                    "📍 ${FormatUtils.formatRate(rate)}" else "📍 ₹/km: —"

                if (trip.totalExtras > 0) {
                    tvExtrasBadge.visibility = View.VISIBLE
                    tvExtrasBadge.text = "+${FormatUtils.formatMoney(trip.totalExtras)} extra"
                } else {
                    tvExtrasBadge.visibility = View.GONE
                }

                btnDeleteTrip.setOnClickListener { onDelete(trip) }

                // Load sub-orders
                getSubOrders(trip.id) { subOrders ->
                    if (subOrders.isNotEmpty()) {
                        tvExpandOrders.visibility = View.VISIBLE
                        tvExpandOrders.setOnClickListener {
                            isExpanded = !isExpanded
                            containerSubOrders.visibility =
                                if (isExpanded) View.VISIBLE else View.GONE
                            tvExpandOrders.text =
                                if (isExpanded) "▲ Hide orders"
                                else "▼ Show ${subOrders.size} orders"
                            if (isExpanded) {
                                llSubOrders.removeAllViews()
                                subOrders.forEach { sub -> addSubOrderView(llSubOrders, sub) }
                            }
                        }
                    } else {
                        tvExpandOrders.visibility = View.GONE
                    }
                }
            }
        }

        private fun addSubOrderView(container: LinearLayout, sub: SubOrder) {
            val ctx = container.context
            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 6, 0, 6)
            }

            // Restaurant name (if different orders have different restaurants)
            if (sub.restaurantName.isNotEmpty()) {
                wrapper.addView(TextView(ctx).apply {
                    text = "🍽 ${sub.restaurantName}"
                    setTextColor(ctx.getColor(R.color.primary))
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            }

            // Drop location
            wrapper.addView(TextView(ctx).apply {
                text = "→ ${sub.dropLocationName}"
                setTextColor(ctx.getColor(R.color.text_primary))
                textSize = 13f
            })

            // Distance + time detail
            wrapper.addView(TextView(ctx).apply {
                text = buildString {
                    if (sub.pickupDistanceKm > 0)
                        append("Pickup: ${FormatUtils.formatKm(sub.pickupDistanceKm)}  ")
                    append("Drop: ${FormatUtils.formatKm(sub.dropDistanceKm)}")
                    if (sub.orderAssignedTime.isNotEmpty())
                        append("  •  ${sub.orderAssignedTime}")
                }
                setTextColor(ctx.getColor(R.color.text_secondary))
                textSize = 12f
            })

            // Divider between sub-orders
            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 6 }
                setBackgroundColor(ctx.getColor(R.color.divider))
            }
            container.addView(wrapper)
            container.addView(divider)
        }
    }

    class TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Trip, newItem: Trip) = oldItem == newItem
    }
}
