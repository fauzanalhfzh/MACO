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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.data.ReceiptItem

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = FinanceRepository(db.dao)
    private val prefs = application.getSharedPreferences("maco_settings", Context.MODE_PRIVATE)

    companion object {
        fun getCurrentMonthIndonesian(): String {
            val months = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
            val c = java.util.Calendar.getInstance()
            return months[c.get(java.util.Calendar.MONTH)].take(3)
        }
        
        fun getCurrentYearInt(): Int {
            return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        }
        
        fun getCurrentPeriodStr(): String {
            return "${getCurrentMonthIndonesian()} ${getCurrentYearInt()}"
        }
    }

    // Selection periods
    private val _selectedMonth = MutableStateFlow(getCurrentMonthIndonesian())
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(getCurrentYearInt())
    val selectedYear = _selectedYear.asStateFlow()

    // Combining Month and Year for database queries
    val selectedPeriod: StateFlow<String> = combine(_selectedMonth, _selectedYear) { m, y ->
        "$m $y"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), getCurrentPeriodStr())

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

    // Theme Preference State (Light Mode)
    private val _isLightTheme = MutableStateFlow(prefs.getBoolean("is_light_theme", false))
    val isLightTheme = _isLightTheme.asStateFlow()

    fun saveThemePreference(isLight: Boolean) {
        _isLightTheme.value = isLight
        prefs.edit().putBoolean("is_light_theme", isLight).apply()
    }

    // Gemini states
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<ReceiptAnalysisResult?>(null)
    val scanResult = _scanResult.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val currentPeriod = getCurrentPeriodStr()
            val txFlow = repository.getTransactionsForPeriod(currentPeriod)
            val planFlow = repository.getBudgetPlans(currentPeriod)
            val currentTxs = txFlow.first()
            val currentPlans = planFlow.first()
            if (currentTxs.isEmpty() && currentPlans.isEmpty()) {
                generateSampleData(currentPeriod)
            }
        }
    }

    private suspend fun generateSampleData(period: String) {
        // Pemasukan Plan
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Pemasukan", name = "Gaji Pokok", plannedAmount = 8000000.0, notes = ""))
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Pemasukan", name = "Bonus", plannedAmount = 2000000.0, notes = ""))
        // Tabungan Plan
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Tabungan", name = "Dana Darurat", plannedAmount = 1000000.0, notes = ""))
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Tabungan", name = "Investasi Saham", plannedAmount = 1500000.0, notes = ""))
        // Pengeluaran Plan
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Makan & Minum", plannedAmount = 2000000.0, notes = ""))
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Transportasi", plannedAmount = 500000.0, notes = ""))
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Hiburan", plannedAmount = 500000.0, notes = ""))
        // Tagihan Plan
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Tagihan", name = "Internet WiFi", plannedAmount = 350000.0, notes = "", dueDate = 15))
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Tagihan", name = "Listrik Pasca", plannedAmount = 400000.0, notes = "", dueDate = 20))
        repository.insertBudgetPlan(BudgetPlan(monthYear = period, category = "Tagihan", name = "Cicilan Mobil", plannedAmount = 2500000.0, notes = "Sisa cicilan: 12x", dueDate = 5))

        val cYear = getCurrentYearInt()
        val cMonthInt = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        
        val now = System.currentTimeMillis()
        val d = { day: Int ->
            val c = java.util.Calendar.getInstance()
            c.set(cYear, cMonthInt, day, 10, 0, 0)
            c.timeInMillis
        }
        val dateStr = { day: Int -> 
            String.format(Locale.getDefault(), "%04d-%02d-%02d", cYear, cMonthInt + 1, day)
        }
        
        // Actual Transactions
        // Pemasukan
        repository.insertTransaction(Transaction(timestamp = d(1), dateString = dateStr(1), monthYear = period, type = "Pemasukan", categoryName = "Gaji Pokok", name = "Gaji bulan ini", amount = 8000000.0, notes = "Gaji bulan ini", isChecked = true))
        repository.insertTransaction(Transaction(timestamp = d(5), dateString = dateStr(5), monthYear = period, type = "Pemasukan", categoryName = "Bonus", name = "Bonus project A", amount = 1500000.0, notes = "Bonus project A", isChecked = true))
        // Tabungan
        repository.insertTransaction(Transaction(timestamp = d(2), dateString = dateStr(2), monthYear = period, type = "Tabungan", categoryName = "Dana Darurat", name = "Transfer dana darurat", amount = 1000000.0, notes = "Transfer dana darurat", isChecked = true))
        // Pengeluaran
        repository.insertTransaction(Transaction(timestamp = d(3), dateString = dateStr(3), monthYear = period, type = "Pengeluaran", categoryName = "Makan & Minum", name = "Makan siang dengan tim", amount = 150000.0, notes = "Makan siang dengan tim", isChecked = false))
        repository.insertTransaction(Transaction(timestamp = d(4), dateString = dateStr(4), monthYear = period, type = "Pengeluaran", categoryName = "Transportasi", name = "Isi bensin", amount = 200000.0, notes = "Isi bensin", isChecked = false))
        repository.insertTransaction(Transaction(timestamp = d(10), dateString = dateStr(10), monthYear = period, type = "Pengeluaran", categoryName = "Makan & Minum", name = "Belanja minimarket", amount = 300000.0, notes = "Belanja bulanan minimarket", isChecked = false))
        // Tagihan
        repository.insertTransaction(Transaction(timestamp = d(12), dateString = dateStr(12), monthYear = period, type = "Tagihan", categoryName = "Internet WiFi", name = "Pembayaran WiFi", amount = 350000.0, notes = "Lunas", isChecked = true))
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

    fun toggleTransactionState(tx: Transaction) {
        viewModelScope.launch {
            if (tx.id == 0) {
                repository.insertTransaction(tx)
            } else {
                repository.insertTransaction(tx.copy(isChecked = !tx.isChecked))
            }
        }
    }

    fun toggleBillTransaction(plan: BudgetPlan, existingTx: Transaction?) {
        viewModelScope.launch {
            if (existingTx == null) {
                // 1. Create a physical paid transaction for this bill
                val now = System.currentTimeMillis()
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDateStr = formatter.format(Date(now))
                val tx = Transaction(
                    timestamp = now,
                    dateString = currentDateStr,
                    monthYear = plan.monthYear,
                    type = "Tagihan",
                    categoryName = plan.name,
                    name = "Bayar ${plan.name}",
                    amount = plan.plannedAmount,
                    isChecked = true,
                    notes = plan.notes,
                    pocket = "Utama"
                )
                repository.insertTransaction(tx)

                // 2. Automatically deduct remainder debt ("Sisa") by installment size
                val currentSisa = plan.notes.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                val newSisa = (currentSisa - plan.plannedAmount).coerceAtLeast(0.0)
                val newNotes = if (newSisa <= 0.0) "Lunas" else "Rp" + String.format(Locale.US, "%,.0f", newSisa).replace(",", ".")
                repository.insertBudgetPlan(plan.copy(notes = newNotes))
            } else {
                // 1. Toggle checked state on existing transaction
                val isCheckedNew = !existingTx.isChecked
                repository.insertTransaction(existingTx.copy(isChecked = isCheckedNew))

                // 2. Adjust remaining debt (Deduct if checking, add/restore if unchecking)
                val currentSisa = plan.notes.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
                val diff = plan.plannedAmount
                val newSisa = if (isCheckedNew) {
                    (currentSisa - diff).coerceAtLeast(0.0)
                } else {
                    currentSisa + diff
                }
                val newNotes = if (newSisa <= 0.0) "Lunas" else "Rp" + String.format(Locale.US, "%,.0f", newSisa).replace(",", ".")
                repository.insertBudgetPlan(plan.copy(notes = newNotes))
            }
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
