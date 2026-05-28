package com.example.data

import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val dao: FinanceDao) {

    fun getAllTransactions(): Flow<List<Transaction>> = dao.getAllTransactions()

    fun getTransactionsForPeriod(monthYear: String): Flow<List<Transaction>> = 
        dao.getTransactionsForPeriod(monthYear)

    suspend fun insertTransaction(transaction: Transaction) {
        dao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(id: Int) {
        dao.deleteTransactionById(id)
    }

    fun getSumByType(monthYear: String, type: String): Flow<Double?> = 
        dao.getSumByType(monthYear, type)

    fun getSumByCategory(monthYear: String, type: String, categoryName: String): Flow<Double?> = 
        dao.getSumByCategory(monthYear, type, categoryName)

    fun getBudgetPlans(monthYear: String): Flow<List<BudgetPlan>> = 
        dao.getBudgetPlans(monthYear)

    suspend fun insertBudgetPlan(plan: BudgetPlan) {
        dao.insertBudgetPlan(plan)
    }

    suspend fun insertBudgetPlans(plans: List<BudgetPlan>) {
        dao.insertBudgetPlans(plans)
    }

    suspend fun deleteBudgetPlan(id: Int) {
        dao.deleteBudgetPlanById(id)
    }

    suspend fun clearBudgetPlansForPeriod(monthYear: String) {
        dao.clearBudgetPlansForPeriod(monthYear)
    }

    suspend fun clearTransactionsForPeriod(monthYear: String) {
        dao.clearTransactionsForPeriod(monthYear)
    }
}
