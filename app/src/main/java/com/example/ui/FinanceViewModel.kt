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
        // Automatically precheck if database has initial values, if not, seeds them for Mei 2026
        viewModelScope.launch {
            selectedPeriod.collect { period ->
                if (period == "Mei 2026") {
                    repository.getBudgetPlans("Mei 2026").collect { plans ->
                        if (plans.isEmpty()) {
                            seedMei2026Data()
                        }
                    }
                }
            }
        }
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

    fun clearAllDataForPeriod() {
        viewModelScope.launch {
            repository.clearBudgetPlansForPeriod(selectedPeriod.value)
            repository.clearTransactionsForPeriod(selectedPeriod.value)
        }
    }

    fun restoreDefaultSpreadsheet() {
        viewModelScope.launch {
            clearAllDataForPeriod()
            if (selectedPeriod.value == "Mei 2026") {
                seedMei2026Data()
            } else {
                seedGenericData(selectedPeriod.value)
            }
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

    private suspend fun seedMei2026Data() {
        val period = "Mei 2026"
        
        // 1. Seed Budget Plans (Rencana Sesuai Spreadsheet Mulai)
        val plans = listOf(
            // Pemasukan (Rencana: Rp6.050.000)
            BudgetPlan(monthYear = period, category = "Pemasukan", name = "Jualan", plannedAmount = 500000.0),
            BudgetPlan(monthYear = period, category = "Pemasukan", name = "Gaji", plannedAmount = 5200000.0),
            BudgetPlan(monthYear = period, category = "Pemasukan", name = "Saham", plannedAmount = 0.0),
            BudgetPlan(monthYear = period, category = "Pemasukan", name = "Bonus", plannedAmount = 100000.0),
            BudgetPlan(monthYear = period, category = "Pemasukan", name = "Freelance", plannedAmount = 250000.0),

            // Tabungan (Rencana: Rp25.000.000)
            BudgetPlan(monthYear = period, category = "Tabungan", name = "Rekening BCA", plannedAmount = 5000000.0),
            BudgetPlan(monthYear = period, category = "Tabungan", name = "Rekening SeaBank", plannedAmount = 10000000.0),
            BudgetPlan(monthYear = period, category = "Tabungan", name = "Dana Darurat", plannedAmount = 10000000.0),

            // Tagihan (Rencana: Rp4.123.029, sisa notes represent target sisa payment)
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Spinjam 4/12", plannedAmount = 356000.0, notes = "Rp2.492.000"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "spinjam 5/12", plannedAmount = 368000.0, notes = "Rp2.198.000"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "spinjam 3/3", plannedAmount = 748000.0, notes = "Rp0"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "spinjam 3/3 (Lain)", plannedAmount = 186432.0, notes = "Rp0"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Spinjam 5/9", plannedAmount = 122710.0, notes = "Rp368.130"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Spaylater", plannedAmount = 845887.0, notes = "Rp118.000"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Sbank 3/12", plannedAmount = 516687.0, notes = "Rp4.650.003"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Sbank 2/12", plannedAmount = 206667.0, notes = "Rp2.066.670"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Sbank 2/12 (Lain)", plannedAmount = 103333.0, notes = "Rp1.033.330"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Pinjaman Mama 2/3", plannedAmount = 353333.0, notes = "Rp353.333"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Sbank 1/12", plannedAmount = 124000.0, notes = "Rp1.364.000"),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Sbank 1/6", plannedAmount = 198000.0, notes = "Rp980.000"),

            // Pengeluaran / Kebutuhan Wajib / Living expenses
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Makan", plannedAmount = 1100000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Kebutuhan Wajib", plannedAmount = 650000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Wifi", plannedAmount = 383000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Olahraga", plannedAmount = 300000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Belanja", plannedAmount = 300000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Service", plannedAmount = 200000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Transportasi", plannedAmount = 150000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Hiburan", plannedAmount = 100000.0)
        )
        repository.insertBudgetPlans(plans)

        // 2. Seed Actual Transactions
        val now = System.currentTimeMillis()
        val txList = listOf(
            // Pemasukan (Aktual: Rp6.381.000)
            Transaction(timestamp = now - 10000, dateString = "2026-05-01", monthYear = period, type = "Pemasukan", categoryName = "Gaji", name = "Gaji Utama", amount = 5781000.0),
            Transaction(timestamp = now - 9500, dateString = "2026-05-15", monthYear = period, type = "Pemasukan", categoryName = "Freelance", name = "Desain Freelance", amount = 600000.0),

            // Tabungan (Aktual: Rp50.000)
            Transaction(timestamp = now - 9000, dateString = "2026-05-01", monthYear = period, type = "Tabungan", categoryName = "Dana Darurat", name = "Setor Darurat", amount = 50000.0),

            // Tagihan (Aktual: Semuanya terbayar lunas Rp4.123.029, isChecked = true sesuaikan spreadsheet!)
            Transaction(timestamp = now - 8500, dateString = "2026-05-05", monthYear = period, type = "Tagihan", categoryName = "Spinjam 4/12", name = "Spinjam 4/12 Lunas", amount = 356000.0, isChecked = true, notes = "Sisa: Rp2.492.000"),
            Transaction(timestamp = now - 8400, dateString = "2026-05-06", monthYear = period, type = "Tagihan", categoryName = "spinjam 5/12", name = "spinjam 5/12 Lunas", amount = 368000.0, isChecked = true, notes = "Sisa: Rp2.198.000"),
            Transaction(timestamp = now - 8300, dateString = "2026-05-07", monthYear = period, type = "Tagihan", categoryName = "spinjam 3/3", name = "spinjam 3/3 Lunas", amount = 748000.0, isChecked = true, notes = "Sisa: Rp0"),
            Transaction(timestamp = now - 8200, dateString = "2026-05-17", monthYear = period, type = "Tagihan", categoryName = "spinjam 3/3 (Lain)", name = "spinjam 3/3 Lain Lunas", amount = 186432.0, isChecked = true, notes = "Sisa: Rp0"),
            Transaction(timestamp = now - 8100, dateString = "2026-05-22", monthYear = period, type = "Tagihan", categoryName = "Spinjam 5/9", name = "Spinjam 5/9 Lunas", amount = 122710.0, isChecked = true, notes = "Sisa: Rp368.130"),
            Transaction(timestamp = now - 8000, dateString = "2026-05-25", monthYear = period, type = "Tagihan", categoryName = "Spaylater", name = "Spaylater Lunas", amount = 845887.0, isChecked = true, notes = "Sisa: Rp118.000"),
            Transaction(timestamp = now - 7900, dateString = "2026-05-28", monthYear = period, type = "Tagihan", categoryName = "Sbank 3/12", name = "Sbank 3/12 Lunas", amount = 516687.0, isChecked = true, notes = "Sisa: Rp4.650.003"),
            Transaction(timestamp = now - 7800, dateString = "2026-05-28", monthYear = period, type = "Tagihan", categoryName = "Sbank 2/12", name = "Sbank 2/12 Lunas", amount = 206667.0, isChecked = true, notes = "Sisa: Rp2.066.670"),
            Transaction(timestamp = now - 7700, dateString = "2026-05-28", monthYear = period, type = "Tagihan", categoryName = "Sbank 2/12 (Lain)", name = "Sbank 2/12 Lunas", amount = 103333.0, isChecked = true, notes = "Sisa: Rp1.033.330"),
            Transaction(timestamp = now - 7600, dateString = "2026-05-28", monthYear = period, type = "Tagihan", categoryName = "Pinjaman Mama 2/3", name = "Mama 2/3 Lunas", amount = 353333.0, isChecked = true, notes = "Sisa: Rp353.333"),
            Transaction(timestamp = now - 7500, dateString = "2026-05-28", monthYear = period, type = "Tagihan", categoryName = "Sbank 1/12", name = "Sbank 1/12 Lunas", amount = 124000.0, isChecked = true, notes = "Sisa: Rp1.364.000"),
            Transaction(timestamp = now - 7400, dateString = "2026-05-28", monthYear = period, type = "Tagihan", categoryName = "Sbank 1/6", name = "Sbank 1/6 Lunas", amount = 198000.0, isChecked = true, notes = "Sisa: Rp980.000"),

            // Pengeluaran / Living Cost Checklists & Lainnya (Aktual: Rp3.421.900)
            Transaction(timestamp = now - 6500, dateString = "2026-05-03", monthYear = period, type = "Pengeluaran", categoryName = "Makan", name = "Uang Makan Utama", amount = 1487300.0),
            Transaction(timestamp = now - 6400, dateString = "2026-05-01", monthYear = period, type = "Pengeluaran", categoryName = "Kebutuhan Wajib", name = "Kontrakan Kost", amount = 650000.0, isChecked = true),
            Transaction(timestamp = now - 6300, dateString = "2026-05-06", monthYear = period, type = "Pengeluaran", categoryName = "Wifi", name = "Bulanan Wifi Indi", amount = 383000.0, isChecked = true),
            Transaction(timestamp = now - 6200, dateString = "2026-05-10", monthYear = period, type = "Pengeluaran", categoryName = "Olahraga", name = "Sewa Lapang & Gym", amount = 267000.0),
            Transaction(timestamp = now - 6100, dateString = "2026-05-12", monthYear = period, type = "Pengeluaran", categoryName = "Belanja", name = "Indomaret Grocery", amount = 250100.0),
            Transaction(timestamp = now - 6000, dateString = "2026-05-18", monthYear = period, type = "Pengeluaran", categoryName = "Service", name = "Ganti Oli Motor", amount = 185000.0),
            Transaction(timestamp = now - 5900, dateString = "2026-05-02", monthYear = period, type = "Pengeluaran", categoryName = "Transportasi", name = "Bensin Pertamax", amount = 149500.0, isChecked = true),
            Transaction(timestamp = now - 5800, dateString = "2026-05-20", monthYear = period, type = "Pengeluaran", categoryName = "Hiburan", name = "Nonton Megamovie", amount = 50000.0)
        )
        
        for (tx in txList) {
            repository.insertTransaction(tx)
        }
    }

    private suspend fun seedGenericData(period: String) {
        // Simple generic budget plans and transactions for non-mei months
        val plans = listOf(
            BudgetPlan(monthYear = period, category = "Pemasukan", name = "Gaji", plannedAmount = 5000000.0),
            BudgetPlan(monthYear = period, category = "Tabungan", name = "Dana Darurat", plannedAmount = 1000000.0),
            BudgetPlan(monthYear = period, category = "Tagihan", name = "Sewa", plannedAmount = 1500000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Makan", plannedAmount = 1200000.0),
            BudgetPlan(monthYear = period, category = "Pengeluaran", name = "Transportasi", plannedAmount = 500000.0)
        )
        repository.insertBudgetPlans(plans)

        val now = System.currentTimeMillis()
        repository.insertTransaction(
            Transaction(timestamp = now, dateString = "25-05-01", monthYear = period, type = "Pemasukan", categoryName = "Gaji", name = "Gaji Bulanan", amount = 5000000.0)
        )
        repository.insertTransaction(
            Transaction(timestamp = now - 1000, dateString = "25-05-02", monthYear = period, type = "Pengeluaran", categoryName = "Makan", name = "Beli Bahan Makanan", amount = 150000.0)
        )
    }
}
