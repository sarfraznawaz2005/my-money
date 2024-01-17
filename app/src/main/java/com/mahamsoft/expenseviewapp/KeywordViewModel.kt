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
    val keywords: LiveData<List<Keyword>> get() = _keywords
    val keywordsFlow: Flow<List<Keyword>> = _keywords.asFlow()

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> get() = _message

    init {
        loadKeywords()
        initializeDefaultKeywords()
    }

    private fun loadKeywords() {
        coroutineScope.launch {
            val keywordsList = withContext(Dispatchers.IO) { databaseHelper.getAllKeywords() }
            _keywords.value = keywordsList
        }
    }

    private fun initializeDefaultKeywords() {
        coroutineScope.launch {
            val existingKeywords = withContext(Dispatchers.IO) { databaseHelper.getAllKeywords() }

            if (existingKeywords.isEmpty()) {
                val defaultKeywords = listOf(
                    Keyword(keyword = "Debited", type = "Debit"),
                    Keyword(keyword = "Credited", type = "Credit")
                )
                defaultKeywords.forEach { databaseHelper.addKeyword(it) }
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
                databaseHelper.getAllKeywords()
            }
            val isKeywordExists = existingKeywords.any {
                it.keyword.equals(trimmedKeyword.keyword, ignoreCase = true)
            }

            if (isKeywordExists) {
                _message.postValue("Keyword already exists")
            } else {
                val result = withContext(Dispatchers.IO) {
                    databaseHelper.addKeyword(trimmedKeyword)
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
                databaseHelper.getAllKeywords()
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
                    databaseHelper.updateKeyword(trimmedKeyword)
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
                databaseHelper.deleteKeyword(id)
            }

            if (result) {
                _message.postValue("Keyword deleted successfully")
            } else {
                _message.postValue("Error deleting keyword")
            }

            loadKeywords()
        }
    }

    fun confirmDeleteKeyword(id: Int) {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                databaseHelper.deleteKeyword(id)
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
        viewModelJob.cancel()
    }
}
