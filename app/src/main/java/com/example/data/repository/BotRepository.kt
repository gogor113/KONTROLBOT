package com.example.data.repository

import com.example.data.api.GridRecommendation
import com.example.data.api.RetrofitClient
import com.example.data.database.BotClient
import com.example.data.database.BotClientDao
import kotlinx.coroutines.flow.Flow

class BotRepository(private val botClientDao: BotClientDao) {
    val allClients: Flow<List<BotClient>> = botClientDao.getAllClients()

    suspend fun insertClient(client: BotClient) {
        botClientDao.insertClient(client)
    }

    suspend fun updateClient(client: BotClient) {
        botClientDao.updateClient(client)
    }

    suspend fun deleteClient(client: BotClient) {
        botClientDao.deleteClient(client)
    }

    suspend fun getClientById(id: Int): BotClient? {
        return botClientDao.getClientById(id)
    }

    /**
     * Obtains recommended parameters from the Gemini API model.
     */
    suspend fun getGeminiOptimization(
        marketTrend: String,
        riskStyle: String,
        balance: Double
    ): GridRecommendation {
        return RetrofitClient.getOptimizedParameters(
            marketTrend = marketTrend,
            riskStyle = riskStyle,
            balance = balance
        )
    }
}
