package com.example.ledgermesh.ui.import_center

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.data.repository.ImportSessionRepository
import com.example.ledgermesh.domain.usecase.ReconciliationEngine
import com.example.ledgermesh.ingestion.csv.CsvColumnMapping
import com.example.ledgermesh.ingestion.csv.CsvImportUseCase
import com.example.ledgermesh.ingestion.csv.CsvPreviewResult
import com.example.ledgermesh.ingestion.csv.ImportResult
import com.example.ledgermesh.ingestion.pdf.PdfImportResult
import com.example.ledgermesh.ingestion.pdf.PdfImportUseCase
import com.example.ledgermesh.ingestion.sms.SmsImportResult
import com.example.ledgermesh.ingestion.sms.SmsImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

sealed class ImportStep {
    data object Idle : ImportStep()
    data class Preview(val result: CsvPreviewResult, val uri: Uri, val fileName: String) : ImportStep()
    data class MappingConfig(
        val preview: CsvPreviewResult,
        val mapping: CsvColumnMapping,
        val uri: Uri,
        val fileName: String
    ) : ImportStep()
    data class Importing(val fileName: String) : ImportStep()
    data class Complete(val result: ImportResult) : ImportStep()
    data class SmsImporting(val message: String = "Scanning SMS messages...") : ImportStep()
    data class SmsComplete(val result: SmsImportResult) : ImportStep()
    data class PdfImporting(val fileName: String) : ImportStep()
    data class PdfComplete(val result: PdfImportResult) : ImportStep()
    data class Error(val message: String) : ImportStep()
}

data class ImportUiState(
    val step: ImportStep = ImportStep.Idle,
    val recentSessions: List<ImportSessionEntity> = emptyList()
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val csvImportUseCase: CsvImportUseCase,
    private val smsImportUseCase: SmsImportUseCase,
    private val pdfImportUseCase: PdfImportUseCase,
    private val importSessionRepository: ImportSessionRepository,
    private val reconciliationEngine: ReconciliationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            importSessionRepository.getRecentFlow(10).collect { sessions ->
                _uiState.update { it.copy(recentSessions = sessions) }
            }
        }
    }

    fun previewCsv(uri: Uri, inputStream: InputStream, fileName: String) {
        viewModelScope.launch {
            try {
                val preview = csvImportUseCase.preview(inputStream, fileName)
                val step = if (preview.suggestedMapping != null) {
                    ImportStep.MappingConfig(preview, preview.suggestedMapping, uri, fileName)
                } else {
                    ImportStep.Preview(preview, uri, fileName)
                }
                _uiState.update { it.copy(step = step) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(step = ImportStep.Error(e.message ?: "Failed to preview file"))
                }
            }
        }
    }

    fun updateMapping(mapping: CsvColumnMapping) {
        val current = _uiState.value.step
        if (current is ImportStep.MappingConfig) {
            _uiState.update { it.copy(step = current.copy(mapping = mapping)) }
        } else if (current is ImportStep.Preview) {
            _uiState.update {
                it.copy(
                    step = ImportStep.MappingConfig(
                        current.result,
                        mapping,
                        current.uri,
                        current.fileName
                    )
                )
            }
        }
    }

    fun confirmImport(inputStream: InputStream) {
        val current = _uiState.value.step
        if (current !is ImportStep.MappingConfig) return

        viewModelScope.launch {
            _uiState.update { it.copy(step = ImportStep.Importing(current.fileName)) }
            try {
                val result = csvImportUseCase.import(inputStream, current.fileName, current.mapping)
                // Run reconciliation after import
                reconciliationEngine.reconcileAll()
                _uiState.update { it.copy(step = ImportStep.Complete(result)) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(step = ImportStep.Error(e.message ?: "Import failed"))
                }
            }
        }
    }

    /**
     * Triggers a full SMS inbox scan. Transitions through SmsImporting -> SmsComplete/Error.
     * The caller is responsible for ensuring READ_SMS permission is granted before invoking.
     */
    fun importSms() {
        viewModelScope.launch {
            _uiState.update { it.copy(step = ImportStep.SmsImporting()) }
            try {
                val result = smsImportUseCase.importAll()
                _uiState.update { it.copy(step = ImportStep.SmsComplete(result)) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(step = ImportStep.Error(e.message ?: "SMS import failed"))
                }
            }
        }
    }

    fun importPdf(inputStream: InputStream, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(step = ImportStep.PdfImporting(fileName)) }
            try {
                val result = pdfImportUseCase.import(inputStream, fileName)
                _uiState.update { it.copy(step = ImportStep.PdfComplete(result)) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(step = ImportStep.Error(e.message ?: "PDF import failed"))
                }
            }
        }
    }

    fun reset() {
        _uiState.update { it.copy(step = ImportStep.Idle) }
    }
}
