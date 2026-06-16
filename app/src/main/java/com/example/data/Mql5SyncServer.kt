package com.example.data

import android.util.Log
import com.example.data.database.BotClient
import com.example.data.repository.BotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections

class Mql5SyncServer(
    private val repository: BotRepository,
    private val onClientUpdated: (BotClient) -> Unit = {},
    private val onLogAdded: (String, String, Boolean) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val port = 9015
    private var isRunning = false

    companion object {
        private const val TAG = "Mql5SyncServer"
    }

    fun start() {
        try {
            if (isRunning) return
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            isRunning = true
            scope.launch(Dispatchers.IO) {
                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        scope.launch(Dispatchers.IO) {
                            handleConnection(socket)
                        }
                    } catch (e: Exception) {
                        // socket closed or error
                    }
                }
            }
            Log.d(TAG, "MQL5 Sync Server started on port $port")
            onLogAdded("Sync Server", "Server Sinkronisasi MQL5 Aktif di http://${getLocalIpAddress()}:$port", false)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MQL5 Sync Server", e)
            onLogAdded("Sync Server", "Gagal mengaktifkan Server MQL5: ${e.localizedMessage}", true)
        }
    }

    fun stop() {
        try {
            isRunning = false
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "MQL5 Sync Server stopped")
            onLogAdded("Sync Server", "Server Sinkronisasi MQL5 dihentikan.", false)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MQL5 Sync Server", e)
        }
    }

    fun isRunning(): Boolean = isRunning

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val sAddr = address.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun getServerUrl(): String {
        return "http://${getLocalIpAddress()}:$port"
    }

    private fun handleConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val firstLine = reader.readLine() ?: return
            
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullPath = parts[1]
            
            var contentLength = 0
            while (true) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
            }
            
            var body = ""
            if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(buffer, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                body = String(buffer)
            }
            
            val pathParts = fullPath.split("?", limit = 2)
            val path = pathParts[0]
            val queryStr = if (pathParts.size > 1) pathParts[1] else null
            
            if (path == "/update") {
                val params = parseParams(queryStr, method, body)
                handleUpdate(socket, params)
            } else {
                handleDefault(socket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun parseParams(query: String?, method: String, body: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        fun parseString(input: String) {
            for (param in input.split("&")) {
                val pair = param.split("=", limit = 2)
                if (pair.size > 1) {
                    try {
                        val key = URLDecoder.decode(pair[0], "UTF-8")
                        val value = URLDecoder.decode(pair[1], "UTF-8")
                        params[key] = value
                    } catch (e: Exception) {}
                }
            }
        }
        
        if (!query.isNullOrEmpty()) {
            parseString(query)
        }
        if (method == "POST" && body.isNotEmpty()) {
            parseString(body)
        }
        return params
    }

    private fun handleUpdate(socket: Socket, params: Map<String, String>) {
        val telegram = params["telegram"] ?: params["telegram_id"] ?: ""
        val name = params["name"] ?: params["user_name"] ?: "MT5 User"
        val balance = params["balance"]?.toDoubleOrNull() ?: 0.0
        val equity = params["equity"]?.toDoubleOrNull() ?: balance
        val drawdown = params["drawdown"]?.toDoubleOrNull() ?: 0.0

        if (telegram.isEmpty()) {
            val responseText = """{"error": "Parameter Telegram ID ('telegram') wajib dikirim untuk pencocokan lisensi."}"""
            sendSocketResponse(socket, 400, "application/json", responseText)
            return
        }

        val formattedTelegram = if (telegram.startsWith("@")) telegram else "@$telegram"

        scope.launch {
            try {
                val allClientsList = repository.allClients.firstOrNull() ?: emptyList()
                var client = allClientsList.find { it.telegram.lowercase() == formattedTelegram.lowercase() }

                if (client == null) {
                    val newClient = BotClient(
                        name = name,
                        telegram = formattedTelegram,
                        balance = balance,
                        equity = equity,
                        drawdown = drawdown,
                        isLicenseActive = true,
                        expiryTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
                        lastActiveTime = System.currentTimeMillis()
                    )
                    repository.insertClient(newClient)
                    Log.d(TAG, "Auto-detected and registered new MQL5 user: $formattedTelegram")
                    onLogAdded("Sync Server", "Klien baru $name ($formattedTelegram) otomatis terdeteksi & terdaftar dari MetaTrader 5.", false)
                    client = newClient
                    onClientUpdated(newClient)
                } else {
                    val updated = client.copy(
                        balance = balance,
                        equity = equity,
                        drawdown = drawdown,
                        lastActiveTime = System.currentTimeMillis()
                    )
                    repository.updateClient(updated)
                    onLogAdded(client.name, "Menerima update MT5 - Balance: $${balance}, drawdown: ${drawdown}%", false)
                    onClientUpdated(updated)
                }

                val isLicenseActiveInt = if (client.isLicenseActive && client.expiryTimestamp > System.currentTimeMillis()) 1 else 0
                val rawModeText = client.activeMode

                val jsonResponse = """
                    {
                      "status": "success",
                      "license_active": $isLicenseActiveInt,
                      "active_mode": "$rawModeText",
                      "initial_lot": ${client.initialLot},
                      "lot_step": ${client.lotStep},
                      "step_points": ${client.stepPoints},
                      "max_spread": ${client.maxSpread},
                      "recovery_threshold": ${client.recoveryThreshold},
                      "exp_multiplier": ${client.expMultiplier}
                    }
                """.trimIndent()

                sendSocketResponse(socket, 200, "application/json", jsonResponse)
            } catch (dbEx: Exception) {
                Log.e(TAG, "Database error during sync", dbEx)
                sendSocketResponse(socket, 500, "application/json", """{"error": "Database error: ${dbEx.localizedMessage}"}""")
            }
        }
    }

    private fun handleDefault(socket: Socket) {
        val responseText = """
            <html>
            <head>
                <title>GOGOR Bot Sync Node</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0E161B; color: #E3E9EC; padding: 40px; text-align: center; }
                    h1 { color: #02C39A; }
                    .card { background: #152026; border: 1px solid #23353F; border-radius: 12px; padding: 24px; max-width: 500px; margin: 40px auto; text-align: left; box-shadow: 0 4px 20px rgba(0,0,0,0.3); }
                    .port { font-family: monospace; font-size: 1.2em; color: #00A8E8; background: #1C2D37; padding: 4px 8px; border-radius: 4px; }
                </style>
            </head>
            <body>
                <h1>GOGOR V12.24 Sync Server</h1>
                <p>Server sinkronisasi MetaTrader 5 (MQL5) Anda aktif dan berjalan dengan aman.</p>
                <div class="card">
                    <h3>Status Node</h3>
                    <p><strong>Device IP Address:</strong> <span class="port">${getLocalIpAddress()}</span></p>
                    <p><strong>Sync Endpoint:</strong> <span class="port">http://${getLocalIpAddress()}:$port/update</span></p>
                    <p><strong>Database Status:</strong> Room DB CONNECTED</p>
                </div>
            </body>
            </html>
        """.trimIndent()
        sendSocketResponse(socket, 200, "text/html", responseText)
    }

    private fun sendSocketResponse(socket: Socket, code: Int, contentType: String, response: String) {
        try {
            val statusText = when (code) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                500 -> "Internal Server Error"
                else -> "OK"
            }
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            val out = socket.getOutputStream()
            
            val header = "HTTP/1.1 $code $statusText\r\n" +
                    "Content-Type: $contentType; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
            
            out.write(header.toByteArray(StandardCharsets.UTF_8))
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing socket response", e)
        }
    }
}
