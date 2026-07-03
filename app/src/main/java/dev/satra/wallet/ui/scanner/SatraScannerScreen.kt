package dev.satra.wallet.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.satra.wallet.R
import dev.satra.wallet.scanner.SatraScanParser
import dev.satra.wallet.scanner.SatraScanPurpose
import dev.satra.wallet.scanner.SatraScanResult
import dev.satra.wallet.settings.SatraSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun SatraScannerScreen(
    purpose: SatraScanPurpose,
    settings: SatraSettings = SatraSettings(),
    onBack: () -> Unit = {},
    onScanResult: (SatraScanResult) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var statusRes by rememberSaveable(purpose.routeSegment) {
        mutableStateOf(R.string.scanner_status_ready)
    }
    val scanLocked = remember(purpose.routeSegment) { AtomicBoolean(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner, previewView, purpose) {
        if (!hasCameraPermission) {
            onDispose {}
        } else {
            val cameraBinding = bindScannerCamera(
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                purpose = purpose,
                scanLocked = scanLocked,
                onValidScan = { result ->
                    performScannerHaptic(hapticFeedback, settings.hapticsEnabled)
                    onScanResult(result)
                },
                onInvalidScan = {
                    statusRes = invalidStatusRes(purpose)
                },
                onCameraUnavailable = {
                    statusRes = R.string.scanner_status_camera_unavailable
                },
            )
            onDispose {
                cameraBinding.dispose()
            }
        }
    }

    ScannerLayout(
        purpose = purpose,
        statusRes = statusRes,
        hasCameraPermission = hasCameraPermission,
        previewView = previewView,
        onBack = {
            performScannerHaptic(hapticFeedback, settings.hapticsEnabled)
            onBack()
        },
        onRequestPermission = {
            performScannerHaptic(hapticFeedback, settings.hapticsEnabled)
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
    )
}

@Composable
private fun ScannerLayout(
    purpose: SatraScanPurpose,
    @StringRes statusRes: Int,
    hasCameraPermission: Boolean,
    previewView: PreviewView,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val compactWidth = maxWidth < 420.dp
        val compactHeight = maxHeight < 720.dp
        val frameSize = when {
            compactWidth -> 238.dp
            compactHeight -> 252.dp
            else -> 286.dp
        }
        val contentMaxWidth = if (maxWidth >= 840.dp) 640.dp else 520.dp
        val cameraContentColor = if (hasCameraPermission) Color.White else MaterialTheme.colorScheme.onSurface

        if (hasCameraPermission) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = if (compactWidth) 20.dp else 28.dp,
                    vertical = if (compactHeight) 14.dp else 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScannerTopBar(
                contentColor = cameraContentColor,
                onBack = onBack,
            )

            if (hasCameraPermission) {
                Spacer(modifier = Modifier.height(if (compactHeight) 18.dp else 28.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ScannerFrame(
                        frameSize = frameSize,
                        strokeColor = Color.White,
                    )
                }
            } else {
                PermissionPanel(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth(),
                    onRequestPermission = onRequestPermission,
                )
            }

            ScannerInstructionPanel(
                purpose = purpose,
                statusRes = statusRes,
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScannerTopBar(
    contentColor: Color,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                tint = contentColor,
            )
        }
        Text(
            text = stringResource(R.string.scanner_screen_title),
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PermissionPanel(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.scanner_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.scanner_permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.scanner_permission_action),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerInstructionPanel(
    purpose: SatraScanPurpose,
    @StringRes statusRes: Int,
    modifier: Modifier = Modifier,
) {
    val copy = scannerCopy(purpose)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(copy.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(copy.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScannerFrame(
    frameSize: Dp,
    strokeColor: Color,
) {
    Canvas(modifier = Modifier.size(frameSize)) {
        val strokeWidth = 5.dp.toPx()
        val cornerLength = size.width * 0.18f
        val radius = 26.dp.toPx()

        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = strokeColor.copy(alpha = 0.38f),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.dp.toPx()),
        )

        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(size.width - cornerLength, 0f), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(size.width, cornerLength), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(cornerLength, size.height), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(0f, size.height - cornerLength), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(size.width, size.height), end = androidx.compose.ui.geometry.Offset(size.width - cornerLength, size.height), strokeWidth = stroke.width, cap = stroke.cap)
        drawLine(strokeColor, start = androidx.compose.ui.geometry.Offset(size.width, size.height), end = androidx.compose.ui.geometry.Offset(size.width, size.height - cornerLength), strokeWidth = stroke.width, cap = stroke.cap)
    }
}

@OptIn(ExperimentalGetImage::class)
private fun bindScannerCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    purpose: SatraScanPurpose,
    scanLocked: AtomicBoolean,
    onValidScan: (SatraScanResult) -> Unit,
    onInvalidScan: () -> Unit,
    onCameraUnavailable: () -> Unit,
): CameraBinding {
    val context = previewView.context
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val analysisExecutor = Executors.newSingleThreadExecutor()
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    cameraProviderFuture.addListener(
        {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { cameraPreview ->
                        cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
                    }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeQrFrame(
                                imageProxy = imageProxy,
                                barcodeScanner = barcodeScanner,
                                purpose = purpose,
                                scanLocked = scanLocked,
                                onValidScan = onValidScan,
                                onInvalidScan = onInvalidScan,
                            )
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                onCameraUnavailable()
            }
        },
        mainExecutor,
    )

    return CameraBinding {
        if (cameraProviderFuture.isDone) {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
        barcodeScanner.close()
        analysisExecutor.shutdown()
    }
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeQrFrame(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    purpose: SatraScanPurpose,
    scanLocked: AtomicBoolean,
    onValidScan: (SatraScanResult) -> Unit,
    onInvalidScan: () -> Unit,
) {
    if (scanLocked.get()) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees,
    )

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val rawValues = barcodes.mapNotNull { barcode -> barcode.rawValue }
            val result = rawValues.firstNotNullOfOrNull { rawValue ->
                SatraScanParser.parseForPurpose(rawValue, purpose)
            }

            when {
                result != null && scanLocked.compareAndSet(false, true) -> onValidScan(result)
                result == null && rawValues.isNotEmpty() -> onInvalidScan()
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private data class ScannerCopy(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private fun scannerCopy(purpose: SatraScanPurpose): ScannerCopy = when (purpose) {
    SatraScanPurpose.Any -> ScannerCopy(
        titleRes = R.string.scanner_any_title,
        bodyRes = R.string.scanner_any_body,
    )
    SatraScanPurpose.RecoveryPhrase -> ScannerCopy(
        titleRes = R.string.scanner_recovery_title,
        bodyRes = R.string.scanner_recovery_body,
    )
    SatraScanPurpose.Address -> ScannerCopy(
        titleRes = R.string.scanner_address_title,
        bodyRes = R.string.scanner_address_body,
    )
    SatraScanPurpose.Payment -> ScannerCopy(
        titleRes = R.string.scanner_payment_title,
        bodyRes = R.string.scanner_payment_body,
    )
}

@StringRes
private fun invalidStatusRes(purpose: SatraScanPurpose): Int = when (purpose) {
    SatraScanPurpose.Any -> R.string.scanner_status_invalid_generic
    SatraScanPurpose.RecoveryPhrase -> R.string.scanner_status_invalid_recovery
    SatraScanPurpose.Address -> R.string.scanner_status_invalid_address
    SatraScanPurpose.Payment -> R.string.scanner_status_invalid_payment
}

private fun performScannerHaptic(
    hapticFeedback: HapticFeedback,
    enabled: Boolean,
) {
    if (enabled) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

private fun interface CameraBinding {
    fun dispose()
}
