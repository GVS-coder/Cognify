package com.cognify.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepository @Inject constructor() {
    
    // Supported currencies
    private val supportedCurrencies = listOf(
        "USD", "EUR", "GBP", "JPY", "INR", "CAD", "AUD", "CHF"
    ).sorted()

    // Short-lived in-memory cache: "USD_INR" -> Pair(Rate, DateString)
    private val rateCache = mutableMapOf<String, Pair<Double, String>>()

    fun getAvailableCurrencies(): List<String> = supportedCurrencies

    suspend fun getLiveRate(fromCurrency: String, toCurrency: String, forceRefresh: Boolean = false): Result<Pair<Double, String>> = withContext(Dispatchers.IO) {
        if (fromCurrency == toCurrency) {
            return@withContext Result.success(Pair(1.0, "Now"))
        }

        val cacheKey = "${fromCurrency}_${toCurrency}"
        
        if (!forceRefresh && rateCache.containsKey(cacheKey)) {
            return@withContext Result.success(rateCache[cacheKey]!!)
        }

        try {
            val url = URL("https://api.frankfurter.dev/v2/rate/$fromCurrency/$toCurrency")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                val date = jsonObject.getString("date")
                val rate = jsonObject.getDouble("rate")
                
                val result = Pair(rate, date)
                rateCache[cacheKey] = result
                
                Result.success(result)
            } else {
                // If there's a cached version, use it as fallback on failure
                if (rateCache.containsKey(cacheKey)) {
                    Result.success(rateCache[cacheKey]!!)
                } else {
                    Result.failure(Exception("Failed to fetch rate: ${connection.responseCode}"))
                }
            }
        } catch (e: Exception) {
            // If there's a cached version, use it as fallback
            if (rateCache.containsKey(cacheKey)) {
                Result.success(rateCache[cacheKey]!!)
            } else {
                Result.failure(e)
            }
        }
    }
}
