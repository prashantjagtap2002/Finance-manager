package com.example.financemanager.domain

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.doubleOrNull

/**
 * Looks up a current price/NAV for a stock or mutual fund symbol. Kept as an interface so the
 * unofficial free source below can be swapped later (e.g. for Kite Connect) without touching any
 * caller — every caller already treats a `null` result as "keep the last cached price".
 */
interface StockPriceService {
    suspend fun getPrice(symbol: String, exchange: String?): Double?
}

/**
 * Reads live prices from Yahoo Finance's public (unofficial, undocumented, key-less) chart
 * endpoint. There is no free official NSE/BSE price API, so this is a pragmatic default — it can
 * change or rate-limit without notice, which is why every caller treats a null/failed lookup as
 * "keep showing the last cached price" rather than an error.
 */
class YahooFinanceStockPriceService : StockPriceService {

    private val client = HttpClient(OkHttp)

    override suspend fun getPrice(symbol: String, exchange: String?): Double? {
        val yahooSymbol = when (exchange?.uppercase()) {
            "BSE" -> "$symbol.BO"
            else -> "$symbol.NS" // default to NSE
        }
        return try {
            val response = client.get("https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol") {
                header("User-Agent", "Mozilla/5.0")
            }
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            json["chart"]?.jsonObject
                ?.get("result")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("meta")?.jsonObject
                ?.get("regularMarketPrice")?.jsonPrimitive?.doubleOrNull
        } catch (e: Exception) {
            null
        }
    }
}
