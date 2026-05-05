package com.delivery.tracker.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Activity-scoped ViewModel — single source of truth for the selected date.
 * Today, History, and Analytics all observe this so switching tabs
 * never loses the date the user was looking at.
 */
@HiltViewModel
class SharedViewModel @Inject constructor() : ViewModel() {

    private val _selectedDateMillis = MutableLiveData(System.currentTimeMillis())
    val selectedDateMillis: LiveData<Long> = _selectedDateMillis

    fun setSelectedDate(millis: Long) {
        _selectedDateMillis.value = millis
    }
}