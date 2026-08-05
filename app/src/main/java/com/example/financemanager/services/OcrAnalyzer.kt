package com.example.financemanager.services

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class OcrResult(
    val merchant: String?,
    val total: Double?,
    val items: List<Pair<String, Double>> = emptyList()
)

object OcrAnalyzer {
    
    fun analyzeReceipt(
        context: Context,
        imageUri: Uri,
        onSuccess: (OcrResult) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                    val result = parseReceiptText(lines)
                    onSuccess(result)
                }
                .addOnFailureListener { e ->
                    onFailure(e)
                }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun parseReceiptText(lines: List<String>): OcrResult {
        if (lines.isEmpty()) return OcrResult(null, null)

        // Heuristic 1: Merchant is usually on the first 1-2 lines
        val merchant = lines.firstOrNull { it.length > 2 && !it.contains(Regex("""\d""")) } ?: "Merchant Store"

        // Heuristic 2: Find total amount
        var total: Double? = null
        val totalRegex = Regex("""(?i)\b(total|net|amount|sum|due|paid|gpay|paytm|bill)\b""")
        val priceRegex = Regex("""\b(\d+(?:\.\d{2})?)\b""")

        // Loop through lines looking for total keywords
        for (i in lines.indices) {
            val line = lines[i].lowercase()
            if (totalRegex.containsMatchIn(line)) {
                // Look for price in this line first
                val match = priceRegex.find(lines[i])
                if (match != null) {
                    total = match.groupValues[1].toDoubleOrNull()
                    break
                } else if (i + 1 < lines.size) {
                    // Check next line
                    val nextMatch = priceRegex.find(lines[i + 1])
                    if (nextMatch != null) {
                        total = nextMatch.groupValues[1].toDoubleOrNull()
                        break
                    }
                }
            }
        }

        // If total not found, take the largest price seen in the receipt (often the total)
        if (total == null) {
            val allPrices = lines.flatMap { line ->
                priceRegex.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            }
            total = allPrices.maxOrNull()
        }

        // Try to extract individual items (lines with a name and a price that are less than total)
        val items = mutableListOf<Pair<String, Double>>()
        val itemLineRegex = Regex("""^(.+?)\s+(\d+(?:\.\d{2})?)$""")
        for (line in lines) {
            val match = itemLineRegex.find(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val price = match.groupValues[2].toDoubleOrNull()
                if (price != null && price < (total ?: Double.MAX_VALUE) && !name.lowercase().contains("total")) {
                    items.add(name to price)
                }
            }
        }

        return OcrResult(merchant, total, items)
    }
}
