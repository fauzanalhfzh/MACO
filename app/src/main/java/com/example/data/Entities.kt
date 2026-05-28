package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val dateString: String, // format "YYYY-MM-DD"
    val monthYear: String, // format "Mei 2126" or "Mei 2026"
    val type: String, // "Pemasukan", "Tabungan", "Pengeluaran", "Tagihan"
    val categoryName: String, // e.g., "Gaji", "Makan", "Spinjam"
    val name: String, // e.g., "Gaji Bulanan", "Makan Siang", "Spaylater"
    val amount: Double,
    val isChecked: Boolean = false, // for checklists in Tagihan & Biaya Hidup
    val notes: String = "" // metadata (SISA for bills, e.g. "Rp2.492.000")
)

@Entity(tableName = "budget_plans")
data class BudgetPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monthYear: String, // format "Mei 2026"
    val category: String, // "Pemasukan", "Tabungan", "Pengeluaran", "Tagihan"
    val name: String, // e.g. "Gaji", "Makan", "Rekening BCA"
    val plannedAmount: Double,
    val notes: String = ""
)

@Dao
interface FinanceDao {

    // --- Transactions ---
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE monthYear = :monthYear ORDER BY timestamp DESC")
    fun getTransactionsForPeriod(monthYear: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("SELECT SUM(amount) FROM transactions WHERE monthYear = :monthYear AND type = :type")
    fun getSumByType(monthYear: String, type: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE monthYear = :monthYear AND type = :type AND categoryName = :categoryName")
    fun getSumByCategory(monthYear: String, type: String, categoryName: String): Flow<Double?>

    // --- Budget Plans ---
    @Query("SELECT * FROM budget_plans WHERE monthYear = :monthYear")
    fun getBudgetPlans(monthYear: String): Flow<List<BudgetPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetPlan(budgetPlan: BudgetPlan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetPlans(budgetPlans: List<BudgetPlan>)

    @Query("DELETE FROM budget_plans WHERE id = :id")
    suspend fun deleteBudgetPlanById(id: Int)
    
    @Query("DELETE FROM budget_plans WHERE monthYear = :monthYear")
    suspend fun clearBudgetPlansForPeriod(monthYear: String)

    @Query("DELETE FROM transactions WHERE monthYear = :monthYear")
    suspend fun clearTransactionsForPeriod(monthYear: String)
}
