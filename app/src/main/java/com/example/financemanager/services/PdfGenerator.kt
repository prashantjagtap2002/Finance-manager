package com.example.financemanager.services

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.data.Transaction
import com.example.financemanager.data.TransactionType
import com.example.financemanager.ui.components.moneyString
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

object PdfGenerator {

    fun generateMonthlyReport(
        context: Context,
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
        monthName: String // e.g. "July 2026"
    ): String? {
        val document = PdfDocument()
        val pageWidth = 595 // A4 Width in pixels at 72 PPI
        val pageHeight = 842 // A4 Height
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var currentPage = document.startPage(pageInfo)
        var currentCanvas: Canvas = currentPage.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Draw Title
        currentCanvas.drawText("Monthly Financial Report", 40f, 60f, paint)

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        currentCanvas.drawText("Month: $monthName", 40f, 90f, paint)
        currentCanvas.drawText("Generated on: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}", 40f, 110f, paint)

        // Summary Statistics
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val net = income - expenses

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        currentCanvas.drawText("Summary", 40f, 160f, paint)

        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        
        paint.color = Color.parseColor("#10B981") // Green
        currentCanvas.drawText("Total Income: ${moneyString(income, false)}", 40f, 190f, paint)
        
        paint.color = Color.parseColor("#F44336") // Red
        currentCanvas.drawText("Total Expenses: ${moneyString(expenses, false)}", 40f, 210f, paint)
        
        paint.color = if (net >= 0) Color.parseColor("#10B981") else Color.parseColor("#F44336")
        currentCanvas.drawText("Net Savings: ${moneyString(net.absoluteValue, false)}" + if (net < 0) " (Loss)" else "", 40f, 230f, paint)

        // Transactions Table Header
        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        var yPos = 280f
        
        currentCanvas.drawText("Transactions", 40f, yPos, paint)
        yPos += 30f
        
        paint.textSize = 12f
        currentCanvas.drawLine(40f, yPos - 15f, pageWidth - 40f, yPos - 15f, paint)
        currentCanvas.drawText("Date", 40f, yPos, paint)
        currentCanvas.drawText("Note / Merchant", 120f, yPos, paint)
        currentCanvas.drawText("Category", 320f, yPos, paint)
        currentCanvas.drawText("Amount", 480f, yPos, paint)
        currentCanvas.drawLine(40f, yPos + 10f, pageWidth - 40f, yPos + 10f, paint)

        yPos += 30f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        
        val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

        for (tx in transactions.sortedByDescending { it.date }) {
            // New Page logic
            if (yPos > pageHeight - 60f) {
                document.finishPage(currentPage)
                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
                currentPage = document.startPage(newPageInfo)
                currentCanvas = currentPage.canvas
                yPos = 60f
                paint.color = Color.BLACK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                
                // Draw headers on new page
                paint.textSize = 14f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                currentCanvas.drawText("Transactions (Contd.)", 40f, yPos, paint)
                yPos += 30f
                paint.textSize = 12f
                currentCanvas.drawLine(40f, yPos - 15f, pageWidth - 40f, yPos - 15f, paint)
                currentCanvas.drawText("Date", 40f, yPos, paint)
                currentCanvas.drawText("Note / Merchant", 120f, yPos, paint)
                currentCanvas.drawText("Category", 320f, yPos, paint)
                currentCanvas.drawText("Amount", 480f, yPos, paint)
                currentCanvas.drawLine(40f, yPos + 10f, pageWidth - 40f, yPos + 10f, paint)
                yPos += 30f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            
            val catName = categories.firstOrNull { it.id == tx.categoryId }?.name ?: "-"
            val noteStr = tx.note.ifEmpty { tx.merchantName ?: "Transaction" }
            val truncatedNote = if (noteStr.length > 25) noteStr.substring(0, 22) + "..." else noteStr
            val dateStr = dateFormatter.format(Date(tx.date))
            
            paint.color = Color.BLACK
            currentCanvas.drawText(dateStr, 40f, yPos, paint)
            currentCanvas.drawText(truncatedNote, 120f, yPos, paint)
            currentCanvas.drawText(catName, 320f, yPos, paint)
            
            val amtColor = when (tx.type) {
                TransactionType.INCOME -> Color.parseColor("#10B981")
                TransactionType.EXPENSE -> Color.parseColor("#F44336")
                TransactionType.TRANSFER -> Color.DKGRAY
            }
            val sign = if (tx.type == TransactionType.EXPENSE) "-" else if (tx.type == TransactionType.INCOME) "+" else ""
            
            paint.color = amtColor
            currentCanvas.drawText("$sign${moneyString(tx.amount, false)}", 480f, yPos, paint)
            
            yPos += 25f
        }

        document.finishPage(currentPage)

        val fileName = "Finance_Report_${monthName.replace(" ", "_")}.pdf"

        // Direct File I/O against the public Downloads directory only works on API < 29;
        // on API 29+ (scoped storage) it silently fails without WRITE_EXTERNAL_STORAGE /
        // MANAGE_EXTERNAL_STORAGE, neither of which this app requests. Route through
        // MediaStore on Q+ instead, which needs no storage permission at all.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null.also { document.close() }
                resolver.openOutputStream(uri)?.use { document.writeTo(it) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                document.close()
                "Downloads/$fileName"
            } catch (e: Exception) {
                e.printStackTrace()
                document.close()
                null
            }
        } else {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                val outputStream = FileOutputStream(file)
                document.writeTo(outputStream)
                document.close()
                outputStream.close()
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                document.close()
                null
            }
        }
    }
}
