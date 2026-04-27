package com.delivery.tracker.ui.analytics

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.delivery.tracker.R
import com.delivery.tracker.utils.FormatUtils
import com.delivery.tracker.viewmodel.RestaurantStat

class RestaurantAdapter(
    private val stats: List<RestaurantStat>
) : RecyclerView.Adapter<RestaurantAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.two_line_list_item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(stats[position], stats.first().orderCount)
    }

    override fun getItemCount() = stats.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(android.R.id.text1)
        private val sub = view.findViewById<TextView>(android.R.id.text2)

        fun bind(stat: RestaurantStat, maxCount: Int) {
            title.text = "🍽 ${stat.name}  •  ${stat.orderCount} orders"
            title.setTextColor(itemView.context.getColor(R.color.text_primary))
            sub.text = "Avg ${FormatUtils.formatMoney(stat.avgOrderPay)} • " +
                       "Avg ${FormatUtils.formatKm(stat.avgDistance)} • " +
                       "Best time: ${stat.bestHour}"
            sub.setTextColor(itemView.context.getColor(R.color.text_secondary))
        }
    }
}
