package com.majeedar.beandoc.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.majeedar.beandoc.R
import java.io.File

private const val TAG = "CameraScreen"

/** The model sees ~224 px; ~1280 px is plenty and keeps capture fast and memory low. */
private val CAPTURE_SIZE = Size(1280, 960)

@Composable
fun CameraScreen(
    onPhotoTaken: (Uri) -> Unit,
    onBack: () -> Unit,
    onGallery: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        // After a denial, no rationale means the system will not show the dialog again: only Settings can help.
        permanentlyDenied = !it && context.findActivity()?.shouldShowRequestPermissionRationale(
            Manifest.permission.CAMERA
        ) == false
    }
    // The user may come back from system settings with the permission changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasCameraPermission() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        if (granted) {
            CameraContent(onPhotoTaken, onBack, onGallery)
        } else {
            PermissionContent(
                permanentlyDenied = permanentlyDenied,
                onAllow = { requestPermission.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.openAppSettings() },
                onBack = onBack,
                onGallery = onGallery,
            )
        }
    }
}

@Composable
private fun CameraContent(
    onPhotoTaken: (Uri) -> Unit,
    onBack: () -> Unit,
    onGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capturing by remember { mutableStateOf(false) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // Preview and capture both 4:3, so the guide square matches the crop of the captured photo.
            val fourByThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            previewResolutionSelector = ResolutionSelector.Builder().setAspectRatioStrategy(fourByThree).build()
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(fourByThree)
                .setResolutionStrategy(
                    ResolutionStrategy(CAPTURE_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    DisposableEffect(lifecycleOwner) {
        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Could not bind camera", e)
            unavailable = true
        }
        controller.initializationFuture.addListener({
            val hasBackCamera = try {
                controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            } catch (e: IllegalStateException) {
                false
            }
            if (!hasBackCamera) unavailable = true
            hasFlash = controller.cameraInfo?.hasFlashUnit() == true
        }, ContextCompat.getMainExecutor(context))
        onDispose { controller.unbind() }
    }

    if (unavailable) {
        UnavailableContent(onBack, onGallery)
        return
    }

    fun takePhoto() {
        if (capturing) return
        capturing = true
        controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        val file = newCaptureFile(context)
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                // The saved JPEG carries the EXIF orientation; ImageLoader applies it like for gallery photos.
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onPhotoTaken(output.savedUri ?: Uri.fromFile(file))
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    capturing = false
                    Toast.makeText(context, R.string.camera_error, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // FIT_CENTER shows the whole photo, so the guide square below is exactly what the model sees.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller // also enables tap to focus
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        GuideOverlay()

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.camera_back))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { flashOn = !flashOn },
                    enabled = hasFlash,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = stringResource(if (flashOn) R.string.camera_flash_on else R.string.camera_flash_off),
                    )
                }
                FilledIconButton(
                    onClick = ::takePhoto,
                    enabled = !capturing,
                    modifier = Modifier.size(84.dp).border(4.dp, Color.White, CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (capturing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    } else {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.camera_shutter),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = onGallery, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.home_gallery))
                }
            }
        }
    }
}

/** Dims everything outside the center square of the (letterboxed, 4:3) preview and shows the hint above it. */
@Composable
private fun GuideOverlay() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // Size of the photo inside the FIT_CENTER preview: 3:4 in portrait, 4:3 in landscape.
        val photoAspect = if (width < height) 3f / 4f else 4f / 3f
        val photoWidth = minOf(width, height * photoAspect)
        val photoHeight = photoWidth / photoAspect
        val side = minOf(photoWidth, photoHeight)
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val square = RoundRect(left, top, left + side, top + side, CornerRadius(24.dp.toPx()))
            val dim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, GeometrySize(size.width, size.height)))
                addRoundRect(square)
            }
            drawPath(dim, Color.Black.copy(alpha = 0.55f))
            drawPath(
                Path().apply { addRoundRect(square) },
                Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
        }
        Text(
            text = stringResource(R.string.camera_hint),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (top - 56.dp.toPx()).coerceAtLeast(64.dp.toPx()).toDp() })
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PermissionContent(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onGallery: () -> Unit,
) {
    MessageContent(
        title = stringResource(R.string.camera_permission_title),
        body = stringResource(
            if (permanentlyDenied) R.string.camera_permission_denied_body else R.string.camera_permission_body
        ),
        primaryLabel = stringResource(
            if (permanentlyDenied) R.string.camera_permission_settings else R.string.camera_permission_allow
        ),
        onPrimary = if (permanentlyDenied) onOpenSettings else onAllow,
        onBack = onBack,
        onGallery = onGallery,
    )
}

@Composable
private fun UnavailableContent(onBack: () -> Unit, onGallery: () -> Unit) {
    MessageContent(
        title = null,
        body = stringResource(R.string.camera_unavailable),
        primaryLabel = null,
        onPrimary = {},
        onBack = onBack,
        onGallery = onGallery,
    )
}

@Composable
private fun MessageContent(
    title: String?,
    body: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    onGallery: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.safeDrawingPadding()) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.camera_back))
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.NoPhotography,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) }
                Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                primaryLabel?.let {
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(it)
                    }
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.home_gallery), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Keeps only the newest capture in cache; earlier ones were already analysed. */
private fun newCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "leaf_${System.currentTimeMillis()}.jpg")
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
