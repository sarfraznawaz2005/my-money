package com.mahamsoft.expenseviewapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class KeywordViewModelFactory(private val databaseHelper: DatabaseHelper) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KeywordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KeywordViewModel(databaseHelper) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
