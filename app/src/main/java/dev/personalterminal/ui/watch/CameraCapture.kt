package dev.personalterminal.ui.watch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.theme.Term
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Full-screen in-app camera (CameraX). Saves a downscaled JPEG to [outputFile] and calls [onCaptured].
 */
@Composable
fun CameraCapture(outputFile: File, onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val p = Term.palette
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var lens by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (hasPermission) {
            AndroidView(
                factory = { c ->
                    PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                },
                update = { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        }.onFailure { error = it.message }
                    }, ContextCompat.getMainExecutor(ctx))
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("camera permission required", color = p.red, style = MaterialTheme.typography.titleMedium)
                Comment("grant it to take wrist shots inside the app")
                TermButton("request again", onClick = { permLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp))
            }
        }
        // Overlay UI
        Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().background(p.bg.copy(alpha = 0.6f)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${Term.prompt} $ capture --wrist", color = p.green, style = MaterialTheme.typography.bodyMedium)
                Text("[flip]", color = p.cyan, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK })
            }
            Column(Modifier.fillMaxWidth().background(p.bg.copy(alpha = 0.7f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                error?.let { Text("error: $it", color = p.red, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TermButton("cancel", color = p.fgDim, onClick = onCancel)
                    TermButton(if (capturing) "…" else "● shutter", filled = true, enabled = hasPermission && !capturing, onClick = {
                        capturing = true
                        val tmp = File(ctx.cacheDir, "camera").apply { mkdirs() }.let { File(it, "raw_${System.currentTimeMillis()}.jpg") }
                        val opts = ImageCapture.OutputFileOptions.Builder(tmp).build()
                        imageCapture.takePicture(opts, executor, object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                runCatching { processPhoto(tmp, outputFile) }
                                    .onSuccess { ContextCompat.getMainExecutor(ctx).execute { capturing = false; onCaptured(outputFile) } }
                                    .onFailure { e -> ContextCompat.getMainExecutor(ctx).execute { capturing = false; error = e.message } }
                                tmp.delete()
                            }
                            override fun onError(exception: ImageCaptureException) {
                                ContextCompat.getMainExecutor(ctx).execute { capturing = false; error = exception.message }
                            }
                        })
                    })
                }
            }
        }
    }
}

/** Downscale to max 1600px on the long edge, apply EXIF rotation, save as JPEG q85. */
fun processPhoto(src: File, dst: File, maxEdge: Int = 1600) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(src.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    val bmp = BitmapFactory.decodeFile(src.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("decode failed")
    val rotation = when (ExifInterface(src.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(bmp.width, bmp.height))
    val matrix = Matrix().apply { postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
    val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    dst.parentFile?.mkdirs()
    FileOutputStream(dst).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    if (out !== bmp) bmp.recycle()
    out.recycle()
}

/** Import an image picked from the gallery into [dst]. */
fun importPhoto(context: Context, uri: android.net.Uri, dst: File) {
    val tmp = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "import_${System.currentTimeMillis()}.jpg") }
    context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(tmp).use { input.copyTo(it) } } ?: error("cannot open image")
    processPhoto(tmp, dst)
    tmp.delete()
}
