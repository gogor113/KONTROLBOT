package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.database.BotClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GoogleSheetSyncService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val TAG = "GoogleSheetSyncService"
        var DEFAULT_SHEET_URL = "https://script.google.com/macros/s/AKfycbyLo2PPukRP-JiX1l-7MJPNZa-UR6oUKmRl6ei4NlMitp4nyV4SxXKq-rjFO8f3gFhv/exec"
    }

    /**
     * Uploads the entire list of clients to the Google Sheets Web App.
     */
    fun uploadAllClients(
        sheetUrl: String,
        clients: List<BotClient>,
        onResult: (Boolean, String) -> Unit
    ) {
        val targetUrl = if (sheetUrl.trim().isEmpty()) DEFAULT_SHEET_URL else sheetUrl.trim()
        val listType = Types.newParameterizedType(List::class.java, BotClient::class.java)
        val adapter = moshi.adapter<List<BotClient>>(listType)
        
        try {
            val jsonClients = adapter.toJson(clients)
            
            // Build dual-payload request (Query String action for direct simple scripts + JSON Body for advanced scripts)
            val buildUrl = if (targetUrl.contains("?")) {
                "$targetUrl&action=sync"
            } else {
                "$targetUrl?action=sync"
            }

            val requestBody = jsonClients.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(buildUrl)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "Failed to upload to Google Sheet", e)
                    onResult(false, e.localizedMessage ?: "Koneksi gagal")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val code = response.code
                    val body = response.body?.string() ?: ""
                    response.close()
                    
                    if (response.isSuccessful || code == 302 || code == 200) {
                        Log.d(TAG, "Uploaded successfully: $body")
                        onResult(true, "Sinkronisasi Berhasil! Sheet terupdate.")
                    } else {
                        Log.e(TAG, "Unsuccessful upload response: $code - $body")
                        onResult(false, "Respon HTTP $code: $body")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error compiling clients JSON", e)
            onResult(false, e.localizedMessage ?: "Kesalahan pemrosesan data")
        }
    }

    /**
     * Fetches details of all clients from the Google Sheets Web App.
     */
    fun downloadAllClients(
        sheetUrl: String,
        onResult: (List<BotClient>?, String) -> Unit
    ) {
        val targetUrl = if (sheetUrl.trim().isEmpty()) DEFAULT_SHEET_URL else sheetUrl.trim()
        
        val buildUrl = if (targetUrl.contains("?")) {
            "$targetUrl&action=read"
        } else {
            "$targetUrl?action=read"
        }

        val request = Request.Builder()
            .url(buildUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to fetch from Google Sheet", e)
                onResult(null, e.localizedMessage ?: "Koneksi gagal")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val code = response.code
                val body = response.body?.string() ?: ""
                response.close()

                if (response.isSuccessful || code == 200) {
                    try {
                        val listType = Types.newParameterizedType(List::class.java, BotClient::class.java)
                        val adapter = moshi.adapter<List<BotClient>>(listType)
                        val fetchedClients = adapter.fromJson(body)
                        if (fetchedClients != null) {
                            onResult(fetchedClients, "Berhasil memuat ${fetchedClients.size} data dari Google Sheet.")
                        } else {
                            // Let's try parsing a nested envelope like {"clients": [...]}
                            val envelopeType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                            val envelopeAdapter = moshi.adapter<Map<String, Any>>(envelopeType)
                            val envelope = envelopeAdapter.fromJson(body)
                            val clientsRaw = envelope?.get("clients")
                            if (clientsRaw != null) {
                                val rawJson = moshi.adapter(Any::class.java).toJson(clientsRaw)
                                val listClients = adapter.fromJson(rawJson)
                                if (listClients != null) {
                                    onResult(listClients, "Berhasil memuat ${listClients.size} data klien.")
                                    return
                                }
                            }
                            onResult(null, "Format respon tidak dikenali.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Google Sheets response content", e)
                        onResult(null, "Gagal parsing JSON: ${e.localizedMessage}")
                    }
                } else {
                    Log.e(TAG, "Unsuccessful fetch: $code - $body")
                    onResult(null, "Respon HTTP $code: $body")
                }
            }
        })
    }

    /**
     * Reports an individual update from MT5 directly to Google Sheet web app for simple instant tracking.
     */
    fun reportClientActivity(
        sheetUrl: String,
        clientObj: BotClient,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val targetUrl = if (sheetUrl.trim().isEmpty()) DEFAULT_SHEET_URL else sheetUrl.trim()
        
        try {
            val buildUrl = (if (targetUrl.contains("?")) "$targetUrl&" else "$targetUrl?") +
                    "action=update_client" +
                    "&telegram=${java.net.URLEncoder.encode(clientObj.telegram, "UTF-8")}" +
                    "&name=${java.net.URLEncoder.encode(clientObj.name, "UTF-8")}" +
                    "&balance=${clientObj.balance}" +
                    "&equity=${clientObj.equity}" +
                    "&drawdown=${clientObj.drawdown}" +
                    "&isLicenseActive=${clientObj.isLicenseActive}" +
                    "&expiryTimestamp=${clientObj.expiryTimestamp}" +
                    "&lastActiveTime=${clientObj.lastActiveTime}" +
                    "&activeMode=${java.net.URLEncoder.encode(clientObj.activeMode, "UTF-8")}" +
                    "&initialLot=${clientObj.initialLot}" +
                    "&lotStep=${clientObj.lotStep}" +
                    "&stepPoints=${clientObj.stepPoints}" +
                    "&maxSpread=${clientObj.maxSpread}" +
                    "&recoveryThreshold=${clientObj.recoveryThreshold}" +
                    "&expMultiplier=${clientObj.expMultiplier}"

            val request = Request.Builder()
                .url(buildUrl)
                .get()
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.d(TAG, "Async report failed: ${e.localizedMessage}")
                    onResult?.invoke(false, e.localizedMessage ?: "Koneksi gagal")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val status = response.isSuccessful
                    val info = response.body?.string() ?: ""
                    response.close()
                    Log.d(TAG, "Async report response: $status - $info")
                    onResult?.invoke(status, "Aktivitas terlaporkan: $info")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sending client activity report", e)
            onResult?.invoke(false, e.localizedMessage ?: "Error formatting URL")
        }
    }
}
