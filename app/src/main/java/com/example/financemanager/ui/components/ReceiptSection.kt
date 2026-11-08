package com.example.financemanager.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financemanager.core.ReceiptStore
import com.example.financemanager.data.Receipt
import com.example.financemanager.theme.*
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Receipt photos for one transaction: thumbnails, an add tile (camera or gallery), and a
 * full-screen viewer with pinch-to-zoom. Gallery picks go through the system Photo Picker, so no
 * storage permission is needed.
 */
@Composable
fun ReceiptSection(viewModel: FinanceViewModel, transactionId: Long) {
    val context = LocalContext.current
    val receipts by remember(transactionId) { viewModel.receiptsFor(transactionId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var viewing by remember { mutableStateOf<Receipt?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // Survives the trip out to the camera app, which can cost this activity its state.
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun attach(uri: Uri, fromCamera: Boolean) {
        saving = true
        viewModel.attachReceipt(transactionId, uri, fromCamera) { saving = false }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (taken && uri != null) attach(uri, fromCamera = true)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attach(uri, fromCamera = false)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Receipts", style = Typography.bodyMedium.copy(color = TextSecondary))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(receipts, key = { it.id }) { receipt ->
                val thumb by produceReceiptBitmap(receipt.fileName, maxEdge = 200)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SubtleSurface)
                        .clickable { viewing = receipt }
                ) {
                    thumb?.let {
                        Image(it, contentDescription = "Receipt photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            item {
                Box {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .clickable(enabled = !saving) { showAddMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SecondaryTeal)
                        else Icon(Icons.Default.AddAPhoto, contentDescription = "Add receipt", tint = SecondaryTeal)
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Take photo") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                val uri = ReceiptStore.newCameraUri(context)
                                pendingCameraUri = uri.toString()
                                camera.launch(uri)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Choose from gallery") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }
                }
            }
        }
    }

    viewing?.let { receipt ->
        ReceiptViewer(
            receipt = receipt,
            onDismiss = { viewing = null },
            onDelete = {
                viewModel.deleteReceipt(receipt)
                viewing = null
            }
        )
    }
}

@Composable
private fun ReceiptViewer(receipt: Receipt, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val image by produceReceiptBitmap(receipt.fileName, maxEdge = 0)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            image?.let {
                Image(
                    it,
                    contentDescription = "Receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transform)
                        // Zoom and pan are read in the draw phase only.
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete receipt", tint = Color.White)
                }
            }
        }
    }

    // The photo file goes the moment this is confirmed and there's no undo, so a stray tap on the
    // icon (next to Close, in a viewer you dismiss constantly) mustn't be enough on its own.
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this receipt?") },
            text = { Text("The photo is removed from this device. The transaction stays.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/** Decodes a stored receipt off the main thread; [maxEdge] 0 means full stored size. */
@Composable
private fun produceReceiptBitmap(fileName: String, maxEdge: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, fileName, maxEdge) {
        value = withContext(Dispatchers.IO) {
            val file = ReceiptStore.file(context, fileName)
            if (!file.exists()) return@withContext null
            val opts = BitmapFactory.Options()
            if (maxEdge > 0) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
                opts.inSampleSize = sample
            }
            BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
        }
    }
}
