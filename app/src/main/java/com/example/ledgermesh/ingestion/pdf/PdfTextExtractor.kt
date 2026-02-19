package com.example.ledgermesh.ingestion.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of extracting text from a PDF document.
 */
data class PdfExtractionResult(
    val pages: List<String>,
    val fullText: String,
    val pageCount: Int,
    val isLikelyScanned: Boolean
)

/**
 * Wraps PdfBox-Android to extract text from PDF bank statements.
 * Uses position-based sorting for better table column alignment.
 */
@Singleton
class PdfTextExtractor @Inject constructor() {

    fun extract(inputStream: InputStream): PdfExtractionResult {
        val document = PDDocument.load(inputStream)
        return try {
            if (document.isEncrypted) {
                throw PdfImportException("This PDF is password-protected. Please provide an unencrypted version.")
            }

            val pageCount = document.numberOfPages
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            val pages = (1..pageCount).map { pageNum ->
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                stripper.getText(document)
            }

            val fullText = pages.joinToString("\n")

            // Detect scanned/image PDFs: if all pages produce negligible text
            val totalChars = pages.sumOf { it.trim().length }
            val isLikelyScanned = pageCount > 0 && totalChars < pageCount * 20

            PdfExtractionResult(
                pages = pages,
                fullText = fullText,
                pageCount = pageCount,
                isLikelyScanned = isLikelyScanned
            )
        } finally {
            document.close()
        }
    }
}

class PdfImportException(message: String) : Exception(message)
