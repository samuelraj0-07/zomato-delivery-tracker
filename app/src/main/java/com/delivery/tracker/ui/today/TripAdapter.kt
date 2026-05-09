package com.delivery.tracker.ui.today

import android.app.AlertDialog
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
    private val getSubOrders: (Long, (List<SubOrder>) -> Unit) -> Unit,
    private val onEdit: ((Trip) -> Unit)? = null
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
                // Issue 7: Incentive trips have restaurantName starting with "🎁 "
                // Show them with a special label so they don't look like restaurants
                val isIncentive = trip.restaurantName.startsWith("🎁")
                tvRestaurant.text = if (isIncentive)
                    trip.restaurantName   // already has "🎁 incentive_pay" label
                else
                    "🍽 ${trip.restaurantName.ifEmpty { "Unknown" }}"

                tvTime.text = trip.assignedTime
                tvOrderPay.text = FormatUtils.formatMoney(trip.orderPay)
                tvDistance.text = FormatUtils.formatKm(trip.screenshotDistance)

                val rate = trip.ratePerKmLive
                tvRatePerKm.text = if (rate > 0)
                    "📍 ${FormatUtils.formatRate(rate)}" else "📍 ₹/km: —"

                // Issue 2: Tap extras badge to see breakdown in a floating dialog
                if (trip.totalExtras > 0) {
                    tvExtrasBadge.visibility = View.VISIBLE
                    tvExtrasBadge.text = "+${FormatUtils.formatMoney(trip.totalExtras)} extra ▾"
                    tvExtrasBadge.setOnClickListener {
                        showExtrasBreakdown(trip)
                    }
                } else {
                    tvExtrasBadge.visibility = View.GONE
                    tvExtrasBadge.setOnClickListener(null)
                }

                btnDeleteTrip.setOnClickListener { onDelete(trip) }

                // Issue 1: Edit button — only show if onEdit callback is provided
                if (onEdit != null) {
                    btnEditTrip.visibility = View.VISIBLE
                    btnEditTrip.setOnClickListener { onEdit.invoke(trip) }
                } else {
                    btnEditTrip.visibility = View.GONE
                }

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

        // Issue 2: Show extras breakdown in a floating AlertDialog
        private fun showExtrasBreakdown(trip: Trip) {
            val ctx = binding.root.context
            val layout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 32, 64, 16)
            }

            if (trip.extraPays.isEmpty()) return

            trip.extraPays.forEach { (key, value) ->
                if (value <= 0) return@forEach
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6, 0, 6)
                }
                val keyLabel = key
                    .replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }

                val tvKey = TextView(ctx).apply {
                    text = keyLabel
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    textSize = 14f
                    setTextColor(ctx.getColor(R.color.text_secondary))
                }
                val tvVal = TextView(ctx).apply {
                    text = FormatUtils.formatMoney(value)
                    textSize = 14f
                    setTextColor(ctx.getColor(R.color.positive))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                row.addView(tvKey)
                row.addView(tvVal)
                layout.addView(row)
            }

            // Divider + total
            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.topMargin = 8; it.bottomMargin = 8 }
                setBackgroundColor(ctx.getColor(R.color.divider))
            }
            val totalRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }
            val tvTotalLabel = TextView(ctx).apply {
                text = "Total"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.text_primary))
            }
            val tvTotalVal = TextView(ctx).apply {
                text = FormatUtils.formatMoney(trip.totalExtras)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.positive))
            }
            totalRow.addView(tvTotalLabel)
            totalRow.addView(tvTotalVal)
            layout.addView(divider)
            layout.addView(totalRow)

            AlertDialog.Builder(ctx)
                .setTitle("Extra Pay Breakdown")
                .setView(layout)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun addSubOrderView(container: LinearLayout, sub: SubOrder) {
            val ctx = container.context
            val wrapper = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 6, 0, 6)
            }

            if (sub.restaurantName.isNotEmpty()) {
                wrapper.addView(TextView(ctx).apply {
                    text = "🍽 ${sub.restaurantName}"
                    setTextColor(ctx.getColor(R.color.primary))
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            }

            wrapper.addView(TextView(ctx).apply {
                text = "→ ${sub.dropLocationName}"
                setTextColor(ctx.getColor(R.color.text_primary))
                textSize = 13f
            })

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
