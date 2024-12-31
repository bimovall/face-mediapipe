package com.example.facemediapipe.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    context: Context,
    val faceLandmarkResultListener: (result: FaceLandmarkerResult, mpImage: MPImage) -> Unit,
    val faceLandmarkErrorListener: (error: RuntimeException) -> Unit) {

    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker(context = context)
    }

    private fun setupFaceLandmarker(context: Context) {
        val baseOptionsBuilder = BaseOptions.builder()

        baseOptionsBuilder.setDelegate(Delegate.CPU)
        baseOptionsBuilder.setModelAssetPath("face_landmarker.task")

        try {
            val baseOptions = baseOptionsBuilder.build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5F)
                .setMinTrackingConfidence(0.5F)
                .setMinFacePresenceConfidence(0.5F)
                .setOutputFaceBlendshapes(true)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setErrorListener(faceLandmarkErrorListener)
                .setResultListener(faceLandmarkResultListener)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)

        } catch (e: IllegalStateException) {

        } catch (e: RuntimeException) {

        }
    }

    fun detect(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun clearFaceLandmarker() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}