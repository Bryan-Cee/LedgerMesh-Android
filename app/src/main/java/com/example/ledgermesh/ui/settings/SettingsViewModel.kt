package com.example.ledgermesh.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledgermesh.data.AppPreferences
import com.example.ledgermesh.data.SmsPreferences
import com.example.ledgermesh.data.db.dao.AggregateDao
import com.example.ledgermesh.data.db.dao.CategoryDao
import com.example.ledgermesh.data.db.dao.ImportSessionDao
import com.example.ledgermesh.data.db.dao.ObservationDao
import com.example.ledgermesh.data.db.dao.OpsLogDao
import com.example.ledgermesh.data.repository.AggregateRepository
import com.example.ledgermesh.ingestion.sms.DefaultProfiles
import com.example.ledgermesh.ingestion.sms.SenderProfile
import com.example.ledgermesh.ingestion.sms.SmsMonitorManager
import com.example.ledgermesh.ui.dashboard.formatTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UI state for the Settings screen.
 */
data class SettingsUiState(
    val autoImportSms: Boolean = false,
    val biometricEnabled: Boolean = false,
    val darkMode: Boolean = false,
    val senderProfiles: List<SenderProfile> = emptyList(),
    val confidenceThreshold: Int = AppPreferences.DEFAULT_CONFIDENCE_THRESHOLD,
    val timeWindowHours: Int = AppPreferences.DEFAULT_TIME_WINDOW_HOURS,
    val amountToleranceCents: Int = AppPreferences.DEFAULT_AMOUNT_TOLERANCE_CENTS,
    val hasPin: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val smsPreferences: SmsPreferences,
    private val smsMonitorManager: SmsMonitorManager,
    private val aggregateRepository: AggregateRepository,
    private val aggregateDao: AggregateDao,
    private val observationDao: ObservationDao,
    private val importSessionDao: ImportSessionDao,
    private val opsLogDao: OpsLogDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            autoImportSms = smsMonitorManager.isEnabled(),
            biometricEnabled = appPreferences.biometricEnabled,
            darkMode = appPreferences.darkMode,
            senderProfiles = DefaultProfiles.getAll(),
            confidenceThreshold = appPreferences.confidenceThreshold,
            timeWindowHours = appPreferences.timeWindowHours,
            amountToleranceCents = appPreferences.amountToleranceCents,
            hasPin = appPreferences.hasPin
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // -- Toggles --

    fun toggleAutoImportSms(enabled: Boolean) {
        if (enabled) {
            smsMonitorManager.enablePeriodicScan()
        } else {
            smsMonitorManager.disablePeriodicScan()
        }
        _uiState.update { it.copy(autoImportSms = enabled) }
    }

    fun toggleBiometric(enabled: Boolean) {
        appPreferences.biometricEnabled = enabled
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        appPreferences.darkMode = enabled
        _uiState.update { it.copy(darkMode = enabled) }
    }

    // -- Reconciliation Settings --

    fun updateConfidenceThreshold(value: Int) {
        appPreferences.confidenceThreshold = value
        _uiState.update { it.copy(confidenceThreshold = value) }
    }

    fun updateTimeWindowHours(value: Int) {
        appPreferences.timeWindowHours = value
        _uiState.update { it.copy(timeWindowHours = value) }
    }

    fun updateAmountToleranceCents(value: Int) {
        appPreferences.amountToleranceCents = value
        _uiState.update { it.copy(amountToleranceCents = value) }
    }

    // -- PIN --

    fun setPin(newPin: String) {
        appPreferences.setPin(newPin)
        _uiState.update { it.copy(hasPin = true, message = "PIN set successfully") }
    }

    fun clearPin() {
        appPreferences.clearPin()
        _uiState.update { it.copy(hasPin = false, message = "PIN removed") }
    }

    fun verifyPin(pin: String): Boolean = appPreferences.verifyPin(pin)

    // -- Export Ledger --

    fun exportLedger(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw Exception("Could not open file for writing")
                    outputStream.bufferedWriter().use { writer ->
                        // CSV header
                        writer.write("Date,Direction,Amount,Currency,Counterparty,Reference,Account,Confidence,Notes")
                        writer.newLine()

                        // Collect all aggregates (one-shot)
                        val aggregates = aggregateRepository.getAllOnce()
                        for (agg in aggregates) {
                            val date = formatTimestamp(agg.canonicalTimestamp)
                            val direction = agg.canonicalDirection.name
                            val amount = "%.2f".format(agg.canonicalAmountMinor / 100.0)
                            val currency = agg.canonicalCurrency
                            val counterparty = csvEscape(agg.canonicalCounterparty ?: "")
                            val reference = csvEscape(agg.canonicalReference ?: "")
                            val account = csvEscape(agg.canonicalAccountHint ?: "")
                            val confidence = agg.confidenceScore.toString()
                            val notes = csvEscape(agg.userNotes ?: "")
                            writer.write("$date,$direction,$amount,$currency,$counterparty,$reference,$account,$confidence,$notes")
                            writer.newLine()
                        }
                    }
                }
                _uiState.update { it.copy(message = "Ledger exported successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Export failed: ${e.message}") }
            }
        }
    }

    // -- Reset All Data --

    fun resetAllData() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear all tables (order matters due to foreign keys)
                    aggregateDao.deleteAllLinks()
                    opsLogDao.deleteAll()
                    aggregateDao.deleteAll()
                    observationDao.deleteAll()
                    importSessionDao.deleteAll()
                    categoryDao.deleteAll()
                    // Reset SMS scan state
                    smsPreferences.lastScanTimestamp = 0L
                }
                _uiState.update { it.copy(message = "All data has been reset") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Reset failed: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
