package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GridRecommendation
import com.example.data.database.AppDatabase
import com.example.data.database.BotClient
import com.example.data.repository.BotRepository
import com.example.data.Mql5SyncServer
import com.example.data.api.GoogleSheetSyncService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface GeminiUiState {
    object Idle : GeminiUiState
    object Loading : GeminiUiState
    data class Success(val recommendation: GridRecommendation) : GeminiUiState
    data class Error(val message: String) : GeminiUiState
}

data class GoogleUserProfile(
    val name: String,
    val email: String,
    val photoUrl: String = "",
    val idToken: String = ""
)

sealed interface AuthState {
    object Unauthenticated : AuthState
    object Loading : AuthState
    data class Authenticated(val profile: GoogleUserProfile) : AuthState
    data class Error(val message: String, val attemptedEmail: String) : AuthState
}

data class BotLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val clientName: String,
    val message: String,
    val isAlert: Boolean = false
)

class BotManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BotRepository
    val clients: StateFlow<List<BotClient>>
    
    private val _geminiState = MutableStateFlow<GeminiUiState>(GeminiUiState.Idle)
    val geminiState: StateFlow<GeminiUiState> = _geminiState.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _logs = MutableStateFlow<List<BotLogEntry>>(emptyList())
    val logs: StateFlow<List<BotLogEntry>> = _logs.asStateFlow()

    private val _isSimulating = MutableStateFlow(true)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _expiringNotifications = MutableStateFlow<List<String>>(emptyList())
    val expiringNotifications: StateFlow<List<String>> = _expiringNotifications.asStateFlow()

    private lateinit var mqlSyncServer: Mql5SyncServer

    private val _isMqlServerRunning = MutableStateFlow(false)
    val isMqlServerRunning: StateFlow<Boolean> = _isMqlServerRunning.asStateFlow()

    private val _mqlServerUrl = MutableStateFlow("")
    val mqlServerUrl: StateFlow<String> = _mqlServerUrl.asStateFlow()

    private val googleSheetSyncService = GoogleSheetSyncService(application)

    private val _googleSheetUrl = MutableStateFlow(GoogleSheetSyncService.DEFAULT_SHEET_URL)
    val googleSheetUrl: StateFlow<String> = _googleSheetUrl.asStateFlow()

    private val _isSheetAutoSyncEnabled = MutableStateFlow(true)
    val isSheetAutoSyncEnabled: StateFlow<Boolean> = _isSheetAutoSyncEnabled.asStateFlow()

    private val _isSyncingSheet = MutableStateFlow(false)
    val isSyncingSheet: StateFlow<Boolean> = _isSyncingSheet.asStateFlow()

    private val _syncStatusText = MutableStateFlow("Siap sinkronisasi")
    val syncStatusText: StateFlow<String> = _syncStatusText.asStateFlow()

    private var simulationJob: Job? = null
    private var licenseSchedulerJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BotRepository(database.botClientDao())

        mqlSyncServer = Mql5SyncServer(
            repository,
            onClientUpdated = { updatedClient ->
                triggerAutoSheetSync(updatedClient)
            },
            onLogAdded = { category, msg, isAlert ->
                addLog(category, msg, isAlert)
            }
        )
        mqlSyncServer.start()
        _isMqlServerRunning.value = mqlSyncServer.isRunning()
        _mqlServerUrl.value = mqlSyncServer.getServerUrl()
        
        clients = repository.allClients
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Seed clients if empty
        viewModelScope.launch {
            clients.collect { list ->
                if (list.isEmpty()) {
                    seedDefaultClients()
                }
            }
        }

        // Start background simulator
        startActivitySimulation()

        // Start background license check scheduler
        startLicenseCheckScheduler()
    }

    private suspend fun seedDefaultClients() {
        val seed = listOf(
            BotClient(
                name = "Andrianto Wijaya",
                telegram = "@andrian13",
                balance = 12500.0,
                equity = 11950.0,
                drawdown = 4.4,
                activeMode = "MODE_BOTH",
                expiryTimestamp = System.currentTimeMillis() + (45L * 24 * 60 * 60 * 1000),
                initialLot = 0.02,
                stepPoints = 1111,
                maxSpread = 500
            ),
            BotClient(
                name = "Budi Santoso",
                telegram = "@budi_s",
                balance = 8400.0,
                equity = 8400.0,
                drawdown = 0.0,
                activeMode = "MODE_BUY_ONLY",
                expiryTimestamp = System.currentTimeMillis() + (12L * 24 * 60 * 60 * 1000),
                initialLot = 0.01,
                stepPoints = 1500,
                maxSpread = 300
            ),
            BotClient(
                name = "Siti Rahma",
                telegram = "@sitirahma_trader",
                balance = 55000.0,
                equity = 52700.0,
                drawdown = 4.18,
                activeMode = "MODE_BOTH",
                expiryTimestamp = System.currentTimeMillis() + (120L * 24 * 60 * 60 * 1000),
                initialLot = 0.05,
                stepPoints = 1111,
                maxSpread = 500
            )
        )
        for (client in seed) {
            repository.insertClient(client)
        }
        addLog("System", "Inisialisasi database bot GOGOR berhasil. Dummy data telah dimuat.")
    }

    fun addClient(name: String, telegram: String, balance: Double, mode: String) {
        viewModelScope.launch {
            val newClient = BotClient(
                name = name,
                telegram = if (telegram.startsWith("@")) telegram else "@$telegram",
                balance = balance,
                equity = balance,
                drawdown = 0.0,
                activeMode = mode,
                expiryTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
            )
            repository.insertClient(newClient)
            addLog("System", "Pengguna baru ${name} ($telegram) berhasil terdaftar.")
            triggerAutoSheetSync(newClient)
        }
    }

    fun updateClient(client: BotClient) {
        viewModelScope.launch {
            repository.updateClient(client)
            addLog("System", "Konfigurasi bot ${client.name} berhasil diperbarui.")
            triggerAutoSheetSync(client)
        }
    }

    fun deleteClient(client: BotClient) {
        viewModelScope.launch {
            repository.deleteClient(client)
            addLog("System", "Pengguna ${client.name} telah dihapus dari sistem kontrol.")
            triggerAutoSheetSync() // trigger full list upload to update delete state of standard list
        }
    }

    fun renewLicense(client: BotClient, days: Int) {
        viewModelScope.launch {
            val updated = client.copy(
                expiryTimestamp = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000),
                isLicenseActive = true
            )
            repository.updateClient(updated)
            addLog(client.name, "Lisensi diperpanjang sebanyak $days hari.")
            triggerAutoSheetSync(updated)
        }
    }

    fun bulkRenewLicenses(clientsToRenew: List<BotClient>, days: Int) {
        viewModelScope.launch {
            clientsToRenew.forEach { client ->
                val updated = client.copy(
                    expiryTimestamp = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000),
                    isLicenseActive = true
                )
                repository.updateClient(updated)
                addLog(client.name, "Lisensi diperpanjang secara masal sebanyak $days hari.")
            }
            triggerAutoSheetSync()
            addLog("System", "Berhasil memperpanjang lisensi ${clientsToRenew.size} subscriber sekaligus.")
        }
    }

    fun bulkUpdateModes(clientsToUpdate: List<BotClient>, newMode: String) {
        viewModelScope.launch {
            clientsToUpdate.forEach { client ->
                val updated = client.copy(
                    activeMode = newMode
                )
                repository.updateClient(updated)
                addLog(client.name, "Mode trading diperbarui secara masal menjadi ${newMode.replace("MODE_", "")}.")
            }
            triggerAutoSheetSync()
            addLog("System", "Berhasil mengubah mode trading ${clientsToUpdate.size} subscriber secara masal.")
        }
    }

    fun getGeminiOptimizations(marketTrend: String, riskStyle: String, currentClient: BotClient) {
        viewModelScope.launch {
            _geminiState.value = GeminiUiState.Loading
            try {
                val recommendation = repository.getGeminiOptimization(
                    marketTrend = marketTrend,
                    riskStyle = riskStyle,
                    balance = currentClient.balance
                )
                _geminiState.value = GeminiUiState.Success(recommendation)
                addLog("Gemini AI", "Konsultasi strategis selesai. Parameter optimal disarankan.")
            } catch (e: Exception) {
                _geminiState.value = GeminiUiState.Error(e.localizedMessage ?: "Unknown error")
                addLog("Gemini AI", "Gagal meminta rekomendasi AI: ${e.localizedMessage}", isAlert = true)
            }
        }
    }

    fun resetGeminiState() {
        _geminiState.value = GeminiUiState.Idle
    }

    fun loginWithGoogle(email: String = "andrytambak13@gmail.com", name: String = "Andrianto Wijaya") {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            delay(1200)
            if (email.trim().lowercase() != "andrytambak13@gmail.com") {
                _authState.value = AuthState.Error(
                    message = "Akses ditolak keras. Sistem mendeteksi upaya masuk yang tidak sah demi menjaga keamanan operasional GOGOR Bot Control.",
                    attemptedEmail = ""
                )
                addLog("Auth System", "Sistem keamanan mendeteksi dan memblokir upaya masuk tidak sah secara aman.")
            } else {
                _authState.value = AuthState.Authenticated(
                    GoogleUserProfile(
                        name = name,
                        email = email,
                        photoUrl = "https://lh3.googleusercontent.com/a/default-user"
                    )
                )
                addLog("Auth System", "Berhasil masuk menggunakan Akun Google terverifikasi: $email ($name)")
            }
        }
    }

    fun resetToLogin() {
        _authState.value = AuthState.Unauthenticated
    }

    fun signOut() {
        viewModelScope.launch {
            val currentProfile = (_authState.value as? AuthState.Authenticated)?.profile
            _authState.value = AuthState.Loading
            delay(600)
            _authState.value = AuthState.Unauthenticated
            addLog("Auth System", "Pengguna ${currentProfile?.name ?: ""} telah keluar.")
        }
    }

    fun toggleSimulation() {
        _isSimulating.value = !_isSimulating.value
        if (_isSimulating.value) {
            startActivitySimulation()
            addLog("Simulator", "Simulasi pasar & robot diaktifkan.")
        } else {
            simulationJob?.cancel()
            addLog("Simulator", "Simulasi dihentikan.")
        }
    }

    private fun addLog(clientName: String, message: String, isAlert: Boolean = false) {
        val entry = BotLogEntry(clientName = clientName, message = message, isAlert = isAlert)
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 50) currentList.removeAt(currentList.lastIndex)
        _logs.value = currentList
    }

    private fun startActivitySimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            val random = Random(System.currentTimeMillis())
            while (true) {
                delay(6000) // update stats every 6 seconds to show dynamic movement
                val currentClients = clients.value
                if (currentClients.isNotEmpty() && _isSimulating.value) {
                    val client = currentClients[random.nextInt(currentClients.size)]
                    
                    // Simulate market fluctuations or bot events
                    val isProfitTick = random.nextBoolean()
                    val eventChance = random.nextInt(100)

                    if (eventChance > 88) {
                        // Resets or CloseAll simulation
                        val updated = client.copy(
                            balance = client.balance + (if (isProfitTick) 150 else -75).toDouble(),
                            equity = client.balance + (if (isProfitTick) 150 else -75).toDouble(),
                            drawdown = 0.0,
                            lastActiveTime = System.currentTimeMillis()
                        )
                        repository.updateClient(updated)
                        addLog(client.name, "Siklus perdagangan selesai. Target harian terpicu. P/L ditutup sukses.", isAlert = false)
                    } else if (eventChance > 60) {
                        // Open new grid layer
                        val currentDrawdown = (client.drawdown + random.nextDouble(0.5, 2.0)).coerceIn(0.0, 18.0)
                        val updated = client.copy(
                            equity = client.balance * (1 - currentDrawdown / 100),
                            drawdown = currentDrawdown,
                            lastActiveTime = System.currentTimeMillis()
                        )
                        repository.updateClient(updated)
                        val nextLayer = random.nextInt(2, 7)
                        addLog(client.name, "Membuka grid layer: GGR-L-${if (client.activeMode.contains("BUY")) "BUY" else "SELL"}-$nextLayer dengan lot ${client.initialLot * 2}", isAlert = currentDrawdown > 10.0)
                    } else {
                        // Gentle tick updates (floating balance action)
                        val newEquity = client.equity + random.nextDouble(-40.0, 50.0)
                        val diff = client.balance - newEquity
                        val newDd = if (diff > 0) (diff / client.balance * 100).coerceIn(0.0, 20.0) else 0.0
                        
                        val updated = client.copy(
                            equity = newEquity.coerceAtLeast(client.balance * 0.5),
                            drawdown = Math.round(newDd * 100.0) / 100.0,
                            lastActiveTime = System.currentTimeMillis()
                        )
                        repository.updateClient(updated)
                        
                        if (random.nextInt(5) == 0) {
                            addLog(client.name, "Menerima detak jantung (Heartbeat ACTIVE). Akun stabil. Spread: ${random.nextInt(120, 280)} pt")
                        }
                    }
                }
            }
        }
    }

    private fun startLicenseCheckScheduler() {
        licenseSchedulerJob?.cancel()
        licenseSchedulerJob = viewModelScope.launch {
            while (true) {
                val currentClients = clients.value
                val expiring = currentClients.filter { client ->
                    val diff = client.expiryTimestamp - System.currentTimeMillis()
                    val days = diff / (24L * 60 * 60 * 1000)
                    client.isLicenseActive && days in 0L..15L
                }
                
                val notifications = expiring.map { client ->
                    val diff = client.expiryTimestamp - System.currentTimeMillis()
                    val days = (diff / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
                    "${client.name} (${days} hari)"
                }
                _expiringNotifications.value = notifications
                
                delay(12000) // Re-run checking task every 12 seconds
            }
        }
    }

    fun toggleMqlServer() {
        if (mqlSyncServer.isRunning()) {
            mqlSyncServer.stop()
            _isMqlServerRunning.value = false
        } else {
            mqlSyncServer.start()
            _isMqlServerRunning.value = true
            _mqlServerUrl.value = mqlSyncServer.getServerUrl()
        }
    }

    fun updateGoogleSheetUrl(url: String) {
        _googleSheetUrl.value = url
    }

    fun toggleSheetAutoSync() {
        _isSheetAutoSyncEnabled.value = !_isSheetAutoSyncEnabled.value
    }

    fun triggerAutoSheetSync(client: BotClient? = null) {
        if (!_isSheetAutoSyncEnabled.value) return
        val url = _googleSheetUrl.value
        if (client != null) {
            googleSheetSyncService.reportClientActivity(url, client) { success, msg ->
                if (!success) {
                    addLog("Google Sheet", "Auto-Sync Gagal: $msg", true)
                }
            }
        } else {
            val list = clients.value
            if (list.isNotEmpty()) {
                googleSheetSyncService.uploadAllClients(url, list) { success, msg ->
                    if (!success) {
                        addLog("Google Sheet", "Auto-Sync Gagal: $msg", true)
                    }
                }
            }
        }
    }

    fun pushAllToGoogleSheet() {
        val url = _googleSheetUrl.value
        val list = clients.value
        if (list.isEmpty()) {
            _syncStatusText.value = "Tidak ada klien untuk dikirim."
            addLog("Google Sheet", "Upload Gagal: Tidak ada data klien.", true)
            return
        }
        _isSyncingSheet.value = true
        _syncStatusText.value = "Mengirim data ke Google Sheet..."
        googleSheetSyncService.uploadAllClients(url, list) { success, message ->
            _isSyncingSheet.value = false
            _syncStatusText.value = message
            addLog("Google Sheet", "Upload Ke Sheet: $message", !success)
        }
    }

    fun pullFromGoogleSheet() {
        val url = _googleSheetUrl.value
        _isSyncingSheet.value = true
        _syncStatusText.value = "Mengambil data dari Google Sheet..."
        googleSheetSyncService.downloadAllClients(url) { sheetClients, message ->
            _isSyncingSheet.value = false
            _syncStatusText.value = message
            if (sheetClients != null) {
                viewModelScope.launch {
                    var updatedCount = 0
                    var addedCount = 0
                    val existingList = clients.value
                    for (sheetClient in sheetClients) {
                        val existing = existingList.find { it.telegram.lowercase() == sheetClient.telegram.lowercase() }
                        if (existing != null) {
                            val merged = sheetClient.copy(id = existing.id)
                            repository.updateClient(merged)
                            updatedCount++
                        } else {
                            val fresh = sheetClient.copy(id = 0)
                            repository.insertClient(fresh)
                            addedCount++
                        }
                    }
                    _syncStatusText.value = "Selesai: $addedCount baru, $updatedCount terupdate."
                    addLog("Google Sheet", "Sinkronisasi Masuk Berhasil: $addedCount baru, $updatedCount terupdate.", false)
                }
            } else {
                addLog("Google Sheet", "Unduh Gagal: $message", true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simulationJob?.cancel()
        licenseSchedulerJob?.cancel()
        if (::mqlSyncServer.isInitialized) {
            mqlSyncServer.stop()
        }
    }
}
