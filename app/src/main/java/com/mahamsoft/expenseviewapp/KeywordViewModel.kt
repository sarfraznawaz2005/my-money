package com.mahamsoft.expenseviewapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeywordViewModel(private val databaseHelper: DatabaseHelper) : ViewModel() {

    private val viewModelJob = Job()
    private val coroutineScope = CoroutineScope(viewModelJob + Dispatchers.Main)

    private val _keywords = MutableLiveData<List<Keyword>>(emptyList())

    val keywordsFlow: Flow<List<Keyword>> = _keywords.asFlow()

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> get() = _message

    private val db by lazy { databaseHelper.writableDatabase }

    init {
        loadKeywords()
        initializeDefaultKeywords()
    }

    private fun loadKeywords() {
        coroutineScope.launch {
            val keywordsList = withContext(Dispatchers.IO) { databaseHelper.getAllKeywords(db) }
            _keywords.value = keywordsList
        }
    }

    private fun initializeDefaultKeywords() {
        coroutineScope.launch {
            val existingKeywords = withContext(Dispatchers.IO) { databaseHelper.getAllKeywords(db) }

            if (existingKeywords.isEmpty()) {
                val defaultKeywords = listOf(
                    Keyword(keyword = "credited", type = "Credit"),
                    Keyword(keyword = "received a deposit", type = "Credit"),
                    Keyword(keyword = "received a transfer", type = "Credit"),
                    Keyword(keyword = "refund of", type = "Credit"),
                    Keyword(keyword = "reversal of", type = "Credit"),
                    Keyword(keyword = "debited", type = "Debit"),
                    Keyword(keyword = "load karnay ka shukria", type = "Debit"),
                    Keyword(keyword = "bill for", type = "Debit"),
                    Keyword(keyword = "bill of", type = "Debit"),
                    Keyword(keyword = "cash withdrawal", type = "Debit"),
                    Keyword(keyword = "fund transfer", type = "Debit"),
                    Keyword(keyword = "pos transaction", type = "Debit"),
                    Keyword(keyword = "charged with pkr", type = "Debit"),
                    Keyword(keyword = "paid bill", type = "Debit"),
                    Keyword(keyword = "paid a kelectric", type = "Debit"),
                    Keyword(keyword = "paid a ssgc", type = "Debit"),
                    Keyword(keyword = "eft of pkr", type = "Debit"),
                    Keyword(keyword = "ibft of pkr", type = "Debit"),
                    Keyword(keyword = "paid successfully", type = "Debit"),
                    Keyword(keyword = "loan payment of", type = "Debit"),
                    Keyword(keyword = "loan repayment of", type = "Debit"),
                    Keyword(keyword = "tax payment of", type = "Debit"),
                    Keyword(keyword = "successfully transferred", type = "Debit"),
                    Keyword(keyword = "you have transferred", type = "Debit"),
                    Keyword(keyword = "your payment of", type = "Debit"),
                    Keyword(keyword = "card payment", type = "Debit"),
                    Keyword(keyword = "card repayment", type = "Debit"),
                    Keyword(keyword = "your scheduled payment of", type = "Debit"),
                )
                defaultKeywords.forEach { databaseHelper.addKeyword(db, it) }
                loadKeywords()
            }
        }
    }

    fun addKeyword(keyword: Keyword) {
        val trimmedKeyword = keyword.copy(keyword = keyword.keyword.trim())
        coroutineScope.launch {
            if (trimmedKeyword.keyword.isBlank()) {
                _message.postValue("Keyword cannot be empty")
                return@launch
            }

            val existingKeywords = withContext(Dispatchers.IO) {
                databaseHelper.getAllKeywords(db)
            }
            val isKeywordExists = existingKeywords.any {
                it.keyword.equals(trimmedKeyword.keyword, ignoreCase = true)
            }

            if (isKeywordExists) {
                _message.postValue("Keyword already exists")
            } else {
                val result = withContext(Dispatchers.IO) {
                    databaseHelper.addKeyword(db, trimmedKeyword)
                }
                if (result) {
                    _message.postValue("Keyword added successfully")
                } else {
                    _message.postValue("Error adding keyword")
                }
                loadKeywords()
            }
        }
    }

    fun updateKeyword(keyword: Keyword) {
        val trimmedKeyword = keyword.copy(keyword = keyword.keyword.trim())
        coroutineScope.launch {
            if (trimmedKeyword.keyword.isBlank()) {
                _message.postValue("Keyword cannot be empty")
                return@launch
            }

            val existingKeywords = withContext(Dispatchers.IO) {
                databaseHelper.getAllKeywords(db)
            }
            val isKeywordExists = existingKeywords.any {
                it.keyword.equals(
                    trimmedKeyword.keyword,
                    ignoreCase = true
                ) && it.id != trimmedKeyword.id
            }

            if (isKeywordExists) {
                _message.postValue("Keyword already exists")
            } else {
                val result = withContext(Dispatchers.IO) {
                    databaseHelper.updateKeyword(db, trimmedKeyword)
                }
                if (result) {
                    _message.postValue("Keyword updated successfully")
                } else {
                    _message.postValue("Error updating keyword")
                }
                loadKeywords()
            }
        }
    }

    fun deleteKeyword(id: Int) {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                databaseHelper.deleteKeyword(db, id)
            }

            if (result) {
                _message.postValue("Keyword deleted successfully")
            } else {
                _message.postValue("Error deleting keyword")
            }

            loadKeywords()
        }
    }

    override fun onCleared() {
        super.onCleared()
        db.close() // Close the database connection
        viewModelJob.cancel()
    }
}
