package eu.hughkennedy.pinvault.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.hughkennedy.pinvault.R
import eu.hughkennedy.pinvault.core.totp.QrCodeDecoder
import eu.hughkennedy.pinvault.core.totp.TotpManager
import eu.hughkennedy.pinvault.core.totp.TotpUriData
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onTotpParsed: (TotpUriData) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var showManualEntryDialog by remember { mutableStateOf(false) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var cameraControl: Camera? by remember { mutableStateOf(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Gallery Photo Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            val bitmap = loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                val decodedText = QrCodeDecoder.decodeBitmap(bitmap)
                if (decodedText != null) {
                    val parsed = TotpManager.parseOtpAuthUri(decodedText)
                    if (parsed != null) {
                        onTotpParsed(parsed)
                        onDismiss()
                        return@rememberLauncherForActivityResult
                    }
                }
            }
            isProcessing = false
            Toast.makeText(context, context.getString(R.string.totp_qr_error_no_code), Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission) {
                    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

                    DisposableEffect(Unit) {
                        onDispose {
                            cameraExecutor.shutdown()
                        }
                    }

                    // Live Camera Viewfinder
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture = capture

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                                    if (!isProcessing) {
                                        val qrText = QrCodeDecoder.decodeImageProxy(imageProxy)
                                        if (qrText != null) {
                                            val parsed = TotpManager.parseOtpAuthUri(qrText)
                                            if (parsed != null) {
                                                isProcessing = true
                                                previewView.post {
                                                    onTotpParsed(parsed)
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    }
                                    imageProxy.close()
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    val cam = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture,
                                        imageAnalysis
                                    )
                                    cameraControl = cam
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Target Reticle Overlay
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .aspectRatio(1f)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                    }
                } else {
                    // Fallback when permission is missing
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.totp_qr_permission_required),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.totp_qr_grant_permission))
                        }
                    }
                }

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    }

                    Text(
                        text = stringResource(R.string.totp_qr_scanner_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    IconButton(
                        onClick = {
                            cameraControl?.let { cam ->
                                isFlashlightOn = !isFlashlightOn
                                cam.cameraControl.enableTorch(isFlashlightOn)
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = if (isFlashlightOn) Color.Yellow else Color.White
                        ),
                        enabled = hasCameraPermission
                    ) {
                        Icon(
                            imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flashlight"
                        )
                    }
                }

                // Subtitle Instruction
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.totp_qr_scanner_subtitle),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Action Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Shutter / Photograph Button
                    if (hasCameraPermission) {
                        Button(
                            onClick = {
                                val capture = imageCapture ?: return@Button
                                isProcessing = true
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val qrText = QrCodeDecoder.decodeImageProxy(image)
                                            image.close()
                                            if (qrText != null) {
                                                val parsed = TotpManager.parseOtpAuthUri(qrText)
                                                if (parsed != null) {
                                                    onTotpParsed(parsed)
                                                    onDismiss()
                                                    return
                                                }
                                            }
                                            isProcessing = false
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.totp_qr_error_no_code),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isProcessing = false
                                            Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.size(68.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.action_take_photo), modifier = Modifier.size(32.dp))
                        }
                    }

                    // Secondary Options: Gallery & Manual Key
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_choose_photo), fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { showManualEntryDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_enter_key_manually), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog for Manual Key Entry
    if (showManualEntryDialog) {
        var manualSecret by remember { mutableStateOf("") }
        var manualIssuer by remember { mutableStateOf("") }
        var isKeyError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showManualEntryDialog = false },
            title = { Text(stringResource(R.string.totp_manual_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = manualIssuer,
                        onValueChange = { manualIssuer = it },
                        label = { Text(stringResource(R.string.totp_manual_issuer_label)) },
                        placeholder = { Text("e.g. GitHub, Google") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = manualSecret,
                        onValueChange = {
                            manualSecret = it.uppercase().replace(" ", "")
                            isKeyError = false
                        },
                        label = { Text(stringResource(R.string.totp_manual_secret_label)) },
                        placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
                        isError = isKeyError,
                        supportingText = if (isKeyError) {
                            { Text("Invalid Base32 Secret Key", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = TotpManager.parseOtpAuthUri(manualSecret)
                        if (parsed != null) {
                            val finalData = if (manualIssuer.isNotBlank()) {
                                parsed.copy(issuer = manualIssuer.trim(), label = manualIssuer.trim())
                            } else {
                                parsed
                            }
                            showManualEntryDialog = false
                            onTotpParsed(finalData)
                            onDismiss()
                        } else {
                            isKeyError = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.totp_manual_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualEntryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Safely decodes a Bitmap from an image Uri across Android versions.
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
