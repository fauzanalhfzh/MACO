package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BudgetPlan
import com.example.data.FinanceRepository
import com.example.data.GeminiService
import com.example.data.ReceiptAnalysisResult
import com.example.data.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = FinanceRepository(db.dao)
    private val prefs = application.getSharedPreferences("maco_settings", Context.MODE_PRIVATE)

    // Selection periods
    private val _selectedMonth = MutableStateFlow("Mei")
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(2026)
    val selectedYear = _selectedYear.asStateFlow()

    // Combining Month and Year for database queries
    val selectedPeriod: StateFlow<String> = combine(_selectedMonth, _selectedYear) { m, y ->
        "$m $y"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Mei 2026")

    // Retrieve transactions for the selected period
    val transactions: StateFlow<List<Transaction>> = selectedPeriod.flatMapLatest { period ->
        repository.getTransactionsForPeriod(period)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Retrieve budget plans for the selected period
    val budgetPlans: StateFlow<List<BudgetPlan>> = selectedPeriod.flatMapLatest { period ->
        repository.getBudgetPlans(period)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // SharedPreferences for user-defined Gemini API Key override
    private val _userApiKey = MutableStateFlow(prefs.getString("gemini_api_key", "") ?: "")
    val userApiKey = _userApiKey.asStateFlow()

    // Gemini states
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<ReceiptAnalysisResult?>(null)
    val scanResult = _scanResult.asStateFlow()

    init {
        // Removed auto-seeding to ensure data can stay empty when deleted
    }

    fun setMonth(month: String) {
        _selectedMonth.value = month
    }

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun saveUserApiKey(key: String) {
        _userApiKey.value = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    // --- Database Operations ---

    fun insertTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(tx)
        }
    }

    fun updateTransaction(tx: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(tx)
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun addBudgetPlan(plan: BudgetPlan) {
        viewModelScope.launch {
            repository.insertBudgetPlan(plan)
        }
    }

    fun deleteBudgetPlan(id: Int) {
        viewModelScope.launch {
            repository.deleteBudgetPlan(id)
        }
    }

    suspend fun clearAllDataForPeriod() {
        repository.clearBudgetPlansForPeriod(selectedPeriod.value)
        repository.clearTransactionsForPeriod(selectedPeriod.value)
    }

    fun deleteAllData() {
        viewModelScope.launch {
            clearAllDataForPeriod()
        }
    }

    // --- Gemini Receipt Scan ---
    fun scanReceiptImage(imageBytes: ByteArray) {
        _isScanning.value = true
        _scanResult.value = null
        
        viewModelScope.launch {
            val key = if (_userApiKey.value.isNotEmpty()) _userApiKey.value else null
            val result = GeminiService.analyzeReceipt(imageBytes, userApiKey = key)
            _scanResult.value = result
            _isScanning.value = false
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    fun confirmReceiptTransaction(itemName: String, amount: Double, category: String, dateString: String) {
        viewModelScope.launch {
            // Parse date to extract period e.g. "Mei 2026"
            val period = parsePeriodFromDate(dateString)
            val tx = Transaction(
                timestamp = System.currentTimeMillis(),
                dateString = dateString,
                monthYear = period,
                type = "Pengeluaran",
                categoryName = category,
                name = itemName,
                amount = amount,
                isChecked = false
            )
            repository.insertTransaction(tx)
            _scanResult.value = null
        }
    }

    private fun parsePeriodFromDate(dateStr: String): String {
        // dateStr is formatted as YYYY-MM-DD
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: Date()
            val monthAndYear = SimpleDateFormat("MMM yyyy", Locale("id", "ID")).format(date)
            // Capitalize (e.g., "Mei 2026")
            monthAndYear.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } catch (e: Exception) {
            selectedPeriod.value
        }
    }

    // --- Seeding Data Methods ---

}
