package com.example.facemediapipe.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.facemediapipe.R
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max

private const val PHOTO_TYPE = "image/jpeg"

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val faceImage = remember {
        mutableStateOf<MPImage?>(null)
    }

    val faceResult = remember {
        mutableStateOf<FaceLandmarkerResult?>(null)
    }

    val faceLandmarkerHelper = remember {
        FaceLandmarkerHelper(context,
            faceLandmarkErrorListener = {
                it.printStackTrace()
            }, faceLandmarkResultListener = { result, mpImage ->
                faceImage.value = mpImage
                faceResult.value = result
            })
    }

    val cameraExecutor = remember { Executors.newSingleThreadScheduledExecutor() }
    val lensFacing = remember {
        mutableStateOf(CameraSelector.LENS_FACING_BACK)
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasPermission = it
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    val preview = androidx.camera.core.Preview.Builder().setResolutionSelector(
        ResolutionSelector.Builder().setAspectRatioStrategy(
            AspectRatioStrategy(
                AspectRatio.RATIO_4_3,
                AspectRatioStrategy.FALLBACK_RULE_AUTO
            )
        ).build()
    ).build()
    // Observe lifecycle changes and log events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d("CameraLifecycle", "Lifecycle event: $event")
            if (event == Lifecycle.Event.ON_DESTROY) {
                Log.d("CameraLifecycle", "Unbinding all CameraX use cases")
                lifecycleOwner.lifecycleScope.launch {
                    cameraExecutor.shutdown()
                    val cameraProvider = context.getCameraProvider()
                    cameraProvider.unbindAll()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraExecutor.shutdown()
            faceLandmarkerHelper.clearFaceLandmarker()
            Log.d("CameraLifecycle", "Removed lifecycle observer")
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_START

        }
    }


    val imageCapture = remember {
        ImageCapture.Builder().build()
    }

    val imageAnalysis = ImageAnalysis.Builder()
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .setBackpressureStrategy(
            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        ).setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(
                        AspectRatio.RATIO_4_3,
                        AspectRatioStrategy.FALLBACK_RULE_AUTO
                    )
                )
                .build()
        )
        .build()

    setImageAnalyzer(imageAnalysis, cameraExecutor, faceLandmarkerHelper, lensFacing.value)

    LaunchedEffect(lensFacing.value) {
        val cameraxSelector = CameraSelector.Builder().requireLensFacing(lensFacing.value).build()

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        //todo disable image analysis in live edit
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraxSelector,
            preview,
            imageCapture,
            imageAnalysis
        )
        preview.surfaceProvider = previewView.surfaceProvider
    }

    Column(modifier = Modifier.background(Color.Black)) {
        CameraPreview(
            previewView,
            hasPermission,
            faceResult = faceResult.value,
            mpImage = faceImage.value,
            openSetting = {
                openAppSettings(context)
            },
            modifier = Modifier
                .weight(1F)
                .background(Color.Black)

        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        BottomSection(
            onImageCaptured = {
                captureImage(imageCapture, cameraExecutor, context, onImageCaptured = {
                    lifecycleOwner.lifecycleScope.launch {
                        snackbarHostState.showSnackbar("Image Saved: $it")
                    }
                }, onErrorCaptured = {
                    lifecycleOwner.lifecycleScope.launch {
                        snackbarHostState.showSnackbar("Error: $it")
                    }
                })
            },
            onCameraFlipped = {
                lensFacing.value = if (lensFacing.value == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }
        )
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    context.startActivity(intent)
}


private fun setImageAnalyzer(
    imageAnalysis: ImageAnalysis,
    cameraExecutor: ExecutorService,
    faceLandmarkerHelper: FaceLandmarkerHelper,
    lensFacing: Int
) {
    imageAnalysis.setAnalyzer(cameraExecutor) { image ->
        try {
            faceLandmarkerHelper.detect(image, lensFacing == CameraSelector.LENS_FACING_FRONT)

        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "Error analyzing image: ${e.message}")
        } finally {
            image.close()
        }
    }
}

private fun captureImage(
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    context: Context,
    onImageCaptured: (String) -> Unit,
    onErrorCaptured: (String) -> Unit
) {
    val filename = "img_${System.currentTimeMillis()}"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, PHOTO_TYPE)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val name = context.getString(R.string.app_name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$name")
        }
    }
    val output = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        output,
        cameraExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onImageCaptured.invoke(outputFileResults.savedUri?.path ?: "")
            }

            override fun onError(exception: ImageCaptureException) {
                onErrorCaptured.invoke(exception.message ?: "")
            }

        }
    )
}

@Composable
fun CameraPermission(openSetting: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(80.dp)
        )
        Text(
            "Allow camera to access this feature",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        TextButton({
            openSetting.invoke()
        }) {
            Text("Open Settings", style = MaterialTheme.typography.bodyLarge)
        }


    }
}

@Composable
fun CameraPreview(
    previewView: View,
    hasPermission: Boolean,
    faceResult: FaceLandmarkerResult?,
    mpImage: MPImage?,
    openSetting: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val width = previewView.width
    val height = previewView.height
    val imageWidth = mpImage?.width ?: 0
    val imageHeight = mpImage?.height ?: 0
    val scaleFactor = max(width * 1F / imageWidth, height * 1F / imageHeight)
    Box(
        modifier = modifier
    ) {
        AndroidView(
            factory = { previewView },
        )

        if (!hasPermission) {
            CameraPermission(
                openSetting = openSetting
            )
        }
        OverlayCamera(
            faceResult = faceResult,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            scaleFactor = scaleFactor
        )

    }
}

@Composable
fun OverlayCamera(
    faceResult: FaceLandmarkerResult?,
    imageWidth: Int,
    imageHeight: Int,
    scaleFactor: Float,
    modifier: Modifier = Modifier
) {
    if (faceResult == null || faceResult.faceLandmarks()?.isEmpty() == true) return

    Canvas(modifier) {

        faceResult.let { faceLandmarkerResult ->
            for (landmark in faceLandmarkerResult.faceLandmarks()) {

            }

            FaceLandmarker.FACE_LANDMARKS_CONNECTORS.forEach {
                val startX =
                    faceLandmarkerResult.faceLandmarks()[0][it.start()].x() * imageWidth * scaleFactor
                val startY =
                    faceLandmarkerResult.faceLandmarks()[0][it.start()].y() * imageHeight * scaleFactor
                val endX =
                    faceLandmarkerResult.faceLandmarks()[0][it.end()].x() * imageWidth * scaleFactor
                val endY =
                    faceLandmarkerResult.faceLandmarks()[0][it.end()].y() * imageHeight * scaleFactor
                drawLine(
                    Color.Red,
                    Offset(startX, startY),
                    Offset(endX, endY),

                    )
            }
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }

@Composable
fun BottomSection(onImageCaptured: () -> Unit, onCameraFlipped: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxWidth()
            .padding(bottom = 16.dp, top = 16.dp),
        contentAlignment = Alignment.CenterEnd

    ) {
        OutlinedButton(
            onClick = onImageCaptured,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.Center),
            border = BorderStroke(8.dp, color = Color.White)
        ) {

        }

        IconButton(
            onClick = onCameraFlipped,
            modifier = Modifier
                .padding(end = 24.dp)
                .size(48.dp)
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(device = Devices.PIXEL_2)
@Composable
fun CameraScreenPreview() {
    CameraScreen()
}