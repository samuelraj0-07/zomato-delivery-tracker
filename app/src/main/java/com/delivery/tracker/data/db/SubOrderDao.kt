package com.delivery.tracker.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.delivery.tracker.data.model.SubOrder

@Dao
interface SubOrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subOrder: SubOrder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subOrders: List<SubOrder>)

    @Delete
    suspend fun delete(subOrder: SubOrder)

    @Query("SELECT * FROM sub_orders WHERE tripId = :tripId ORDER BY orderNumber ASC")
    fun getSubOrdersForTrip(tripId: Long): LiveData<List<SubOrder>>

    @Query("SELECT * FROM sub_orders WHERE tripId = :tripId ORDER BY orderNumber ASC")
    suspend fun getSubOrdersForTripOnce(tripId: Long): List<SubOrder>

    @Query("DELETE FROM sub_orders WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: Long)
}
