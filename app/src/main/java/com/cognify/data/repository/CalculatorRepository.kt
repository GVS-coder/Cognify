package com.cognify.data.repository

import com.cognify.data.local.CalculatorHistoryDao
import com.cognify.data.local.CalculatorHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

import com.google.firebase.auth.FirebaseAuth

class CalculatorRepository @Inject constructor(
    private val calculatorHistoryDao: CalculatorHistoryDao,
    private val auth: FirebaseAuth
) {
    fun getHistory(): Flow<List<CalculatorHistoryEntity>> = calculatorHistoryDao.getAllHistory(auth.currentUser?.uid ?: "")

    suspend fun addHistory(expression: String, result: String) {
        val entity = CalculatorHistoryEntity(
            expression = expression,
            result = result,
            timestamp = System.currentTimeMillis(),
            userId = auth.currentUser?.uid ?: ""
        )
        calculatorHistoryDao.insertHistory(entity)
    }

    suspend fun clearHistory() {
        calculatorHistoryDao.clearHistory(auth.currentUser?.uid ?: "")
    }
}
