package com.tutpro.baresip.plus

import android.Manifest
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import androidx.annotation.RequiresPermission
import java.nio.ByteBuffer

class Camera2(
    private val w: Int,
    private val h: Int,
    private val fps: Int,
    private val userData: Long
) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var isRunning = false

    fun startBackground() {
        bgThread = HandlerThread("CameraBg").apply { start() }
        bgHandler = Handler(bgThread!!.looper)
    }

    fun stopBackground() {
        bgThread?.let {
            it.quitSafely()
            try {
                it.join()
            } catch (_: InterruptedException) {
            }
        }
        bgThread = null
        bgHandler = null
        Log.d("Camera2", "stopBackground")
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @Suppress("unused")
    fun startCamera(previewSurface: Surface?, facing: Int) {
        this.previewSurface = previewSurface
        startBackground()
        imageReader = ImageReader.newInstance(w, h, ImageFormat.YUV_420_888, 3).apply {
            setOnImageAvailableListener(imageAvailListener, bgHandler)
        }
        isRunning = true
        try {
            val cameraId = getCameraId(facing)
            cameraId?.let  {
                cameraManager?.openCamera(it, camStateCallback, bgHandler)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    @Throws(CameraAccessException::class)
    private fun getCameraId(facing: Int): String? {
        cameraManager?.cameraIdList?.forEach { id ->
            val c = cameraManager!!.getCameraCharacteristics(id)
            val lensFacing = c.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing != null && lensFacing == facing) return id
        }
        return null
    }

    @Suppress("unused")
    fun stopCamera() {
        Log.d("Camera2", "stopCamera")
        if (!isRunning) return
        isRunning = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    private val imageAvailListener = ImageReader.OnImageAvailableListener { reader ->
        if (!isRunning) return@OnImageAvailableListener
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

        val planes = image.planes
        val plane0: ByteBuffer = planes[0].buffer
        val plane1: ByteBuffer? = if (planes.size > 1) planes[1].buffer else null
        val plane2: ByteBuffer? = if (planes.size > 2) planes[2].buffer else null

        PushFrame(
            userData,
            plane0,
            planes[0].rowStride,
            planes[0].pixelStride,
            plane1,
            plane1?.let { planes[1].rowStride } ?: 0,
            plane1?.let { planes[1].pixelStride } ?: 0,
            plane2,
            plane2?.let { planes[2].rowStride } ?: 0,
            plane2?.let { planes[2].pixelStride } ?: 0
        )

        image.close()
    }

    private val camStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            try {
                val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val targets = mutableListOf<Surface>()

                previewSurface?.let {
                    builder.addTarget(it)
                    targets.add(it)
                }

                imageReader?.surface?.let {
                    builder.addTarget(it)
                    targets.add(it)
                }

                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))

                camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), null, bgHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // You can add callbacks or logs
                    }
                }, bgHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
        }

        override fun onClosed(camera: CameraDevice) {
            Log.d("Camera2", "onClosed")
            stopBackground()
        }
    }

    external fun PushFrame(
        userData: Long,
        plane0: ByteBuffer, rowStride0: Int, pixStride0: Int,
        plane1: ByteBuffer?, rowStride1: Int, pixStride1: Int,
        plane2: ByteBuffer?, rowStride2: Int, pixStride2: Int
    )

    companion object {
        private var cameraManager: CameraManager? = null

        @JvmStatic
        fun setCameraManager(cm: CameraManager) {
            cameraManager = cm
        }

    }
}
