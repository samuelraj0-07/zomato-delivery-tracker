package com.delivery.tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tds_entries")
data class TdsEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekLabel: String,          // "Week 1 - Apr 2026"
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val amount: Double,
    val dateMillis: Long
)
