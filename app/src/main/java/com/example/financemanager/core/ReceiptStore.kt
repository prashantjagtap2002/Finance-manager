package com.example.financemanager.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Receipt photos on disk: private to the app, re-encoded small enough that a year of receipts
 * costs tens of megabytes rather than gigabytes.
 *
 * A Pixel camera photo is 12 MP and 3–5 MB. Nothing about a receipt needs that — 1600 px on the
 * long edge keeps small print legible and JPEG at 80 lands around 200–350 KB.
 */
object ReceiptStore {

    private const val DIR = "receipts"
    private const val CAMERA_DIR = "camera"
    private const val MAX_EDGE = 1600
    private const val JPEG_QUALITY = 80

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * A fresh content Uri the camera app can write a full-size photo into. Lives in the cache, and
     * is deleted as soon as [attach] has made the compressed copy.
     */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Compresses the image at [source] into private storage and records it against the transaction. */
    suspend fun attach(context: Context, transactionId: Long, source: Uri, deleteSource: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val bitmap = runCatching { decodeScaled(context, source) }.getOrNull() ?: return@withContext false
            val name = "${UUID.randomUUID()}.jpg"
            val ok = runCatching {
                file(context, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            }.getOrDefault(false)
            bitmap.recycle()
            if (deleteSource) runCatching { context.contentResolver.delete(source, null, null) }
            if (!ok) return@withContext false
            FinanceDatabase.getDatabase(context).receiptDao().insert(Receipt(transactionId = transactionId, fileName = name))
            true
        }

    suspend fun delete(context: Context, receipt: Receipt) = withContext(Dispatchers.IO) {
        FinanceDatabase.getDatabase(context).receiptDao().delete(receipt)
        file(context, receipt.fileName).delete()
    }

    /** What each receipt's transaction looks like — amount and date — keyed by transaction id. */
    suspend fun fingerprints(context: Context): Map<Long, Pair<Double, Long>> = withContext(Dispatchers.IO) {
        val db = FinanceDatabase.getDatabase(context)
        db.receiptDao().allTransactionIds().distinct().mapNotNull { id ->
            db.financeDao().getTransactionById(id)?.let { id to (it.amount to it.date) }
        }.toMap()
    }

    /**
     * After a backup restore, drops receipts whose transaction id now names a different
     * transaction. Receipts are not in the backup, and a restore re-inserts transactions under
     * the backup's ids, so a photo would otherwise be shown against whatever unrelated row now
     * holds its old id. A restore of this device's own backup keeps every receipt whose row came
     * back unchanged.
     */
    suspend fun dropMismatched(context: Context, before: Map<Long, Pair<Double, Long>>) = withContext(Dispatchers.IO) {
        val db = FinanceDatabase.getDatabase(context)
        val dao = db.receiptDao()
        before.forEach { (transactionId, fingerprint) ->
            val now = db.financeDao().getTransactionById(transactionId)
            val same = now != null && kotlin.math.abs(now.amount - fingerprint.first) < 0.005 && now.date == fingerprint.second
            if (!same) dao.receiptsForOnce(transactionId).forEach { delete(context, it) }
        }
    }

    /**
     * Removes receipts whose transaction is gone, and files no row points at. Run on launch rather
     * than on delete, so the Undo after deleting a transaction still has its photo to come back to.
     */
    suspend fun cleanUp(context: Context) = withContext(Dispatchers.IO) {
        val dao = FinanceDatabase.getDatabase(context).receiptDao()
        dao.orphans().forEach { delete(context, it) }
        val known = dao.allFileNames().toSet()
        dir(context).listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
        File(context.cacheDir, CAMERA_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF rotation itself, so a portrait photo stays upright,
            // and it can scale during decode instead of inflating 12 MP first.
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
