package com.tutpro.baresip.plus;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Camera2 {
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private ImageReader imageReader;
    private Surface previewSurface;
    private static CameraManager cameraManager = null;


    private boolean isRunning = false;
    private final long userData;
    private final int fps;
    private final int w;
    private final int h;

    public static void SetCameraManager(CameraManager cm) {
        cameraManager = cm;
    }

    public static CameraManager GetCameraManager() {
        return cameraManager;
    }

    public Camera2(int w_, int h_, int fps_, long userData_) {
        w = w_;
        h = h_;
        userData = userData_;
        fps = fps_;
    }

    public void startBackground() {
        bgThread = new HandlerThread("CameraBg");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    public void stopBackground() {
        if (bgThread != null) {
            bgThread.quitSafely();
            try {
                bgThread.join();
            } catch (InterruptedException e) {
            }
            bgThread = null;
            bgHandler = null;
        }
    }

    public void startCamera(Surface previewSurface, int facing) {
        this.previewSurface = previewSurface;
        startBackground();
        imageReader = ImageReader.newInstance(w, h, ImageFormat.YUV_420_888, 3);
        imageReader.setOnImageAvailableListener(imageAvailListener, bgHandler);
        isRunning = true;
        try {
            String cameraId = getCameraId(facing);
            cameraManager.openCamera(cameraId, camStateCallback, bgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private String getCameraId(int facing) throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing != null && lensFacing == facing) return id;
        }
        return null;
    }

    public void stopCamera() {
        if (!isRunning) return;

        isRunning = false;
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        stopBackground();
    }


    ImageReader.OnImageAvailableListener imageAvailListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (!isRunning) return;

            Image image = reader.acquireLatestImage();
            if (image == null) return;

            /* Get planes buffers. According to the docs, the buffers are always direct buffers */
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer plane0 = planes[0].getBuffer();
            ByteBuffer plane1 = planes.length > 1 ? planes[1].getBuffer() : null;
            ByteBuffer plane2 = planes.length > 2 ? planes[2].getBuffer() : null;
            assert plane0.isDirect();

            //for (Image.Plane p: planes) {
            //  Log.d(TAG, String.format("size=%d bytes, getRowStride()=%d getPixelStride()=%d", p.getBuffer().remaining(), p.getRowStride(), p.getPixelStride()));
            //}

            PushFrame(userData, plane0, planes[0].getRowStride(), planes[0].getPixelStride(), plane1, plane1 != null ? planes[1].getRowStride() : 0, plane1 != null ? planes[1].getPixelStride() : 0, plane2, plane2 != null ? planes[2].getRowStride() : 0, plane2 != null ? planes[2].getPixelStride() : 0);

            image.close();
        }
    };

    private final CameraDevice.StateCallback camStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            try {
                CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                // There is a previewSurface to add
                List<Surface> targets = new ArrayList<>();
                if (previewSurface != null) {
                    builder.addTarget(previewSurface);
                    targets.add(previewSurface);
                }

                // ImageReader must be added
                builder.addTarget(imageReader.getSurface());
                targets.add(imageReader.getSurface());

                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(fps, fps));

                camera.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        captureSession = session;
                        try {
                            session.setRepeatingRequest(builder.build(), null, bgHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        // You can add callbacks or logs
                    }
                }, bgHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
        }
    };

    native void PushFrame(long userData_, ByteBuffer plane0, int rowStride0, int pixStride0, ByteBuffer plane1, int rowStride1, int pixStride1, ByteBuffer plane2, int rowStride2, int pixStride2);

}
