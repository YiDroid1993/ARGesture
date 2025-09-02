// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/CameraHelper.java
// 描述: [已重构] 封装所有相机底层操作的帮助类，并修复崩溃问题。
// =================================================================================
package com.yidroid.argesture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class CameraHelper {

    public interface CameraListener {
        void onImageAvailable(Bitmap bitmap);
        void onCameraConfigured(String cameraId, int sensorRotation, int facing);
        void onCameraError(String message);
    }

    private static final String TAG = "CameraHelper";
    private final Context context;
    private final CameraListener listener;
    private final GestureSettings settings;
    private final YuvToRgbConverter yuvToRgbConverter;

    private final CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private final AtomicBoolean isCameraOpening = new AtomicBoolean(false);
    private String activeCameraId;
    private Surface activeSurface;
    private volatile boolean isStopping = false;

    public CameraHelper(Context context, CameraListener listener) {
        this.context = context;
        this.listener = listener;
        this.settings = GestureSettings.getInstance(context);
        this.yuvToRgbConverter = new YuvToRgbConverter(context);
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void start() {
        startCameraThread();
    }

    public void stop() {
        stopCamera();
        stopCameraThread();
    }

    @SuppressLint("MissingPermission")
    public void startCamera(Surface previewSurface) {
        if (isCameraOpening.getAndSet(true)) return;
        isStopping = false;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listener.onCameraError("Camera permission not granted.");
            isCameraOpening.set(false);
            return;
        }

        cameraHandler.post(() -> {
            try {
                String selectedCameraId = findCamera(CameraCharacteristics.LENS_FACING_FRONT);
                if (selectedCameraId == null) {
                    selectedCameraId = findCamera(CameraCharacteristics.LENS_FACING_BACK);
                }

                if (selectedCameraId == null) {
                    listener.onCameraError("No available camera found.");
                    isCameraOpening.set(false);
                    return;
                }

                activeCameraId = selectedCameraId;
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(activeCameraId);
                int sensorRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                listener.onCameraConfigured(activeCameraId, sensorRotation, facing);

                imageReader = ImageReader.newInstance(settings.CAMERA_WIDTH, settings.CAMERA_HEIGHT, ImageFormat.YUV_420_888, 2);
                imageReader.setOnImageAvailableListener(this::onImageAvailable, cameraHandler);

                cameraManager.openCamera(activeCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        if (isStopping) { camera.close(); return; }
                        isCameraOpening.set(false);
                        cameraDevice = camera;
                        activeSurface = previewSurface;
                        createCaptureSession(previewSurface);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        isCameraOpening.set(false);
                        camera.close();
                        if (cameraDevice == camera) cameraDevice = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        isCameraOpening.set(false);
                        listener.onCameraError("Camera device error: " + error);
                        camera.close();
                        if (cameraDevice == camera) cameraDevice = null;
                    }
                }, cameraHandler);
            } catch (CameraAccessException e) {
                isCameraOpening.set(false);
                listener.onCameraError("Failed to access camera: " + e.getMessage());
            }
        });
    }

    public void stopCamera() {
        isStopping = true;
        try {
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
            if (imageReader != null) { imageReader.close(); imageReader = null; }
        } catch (Exception e) {
            Log.e(TAG, "Error closing camera resources", e);
        }
    }

    private void onImageAvailable(ImageReader reader) {
        try (Image image = reader.acquireLatestImage()) {
            if (image != null && listener != null) {
                Bitmap bitmap = yuvToRgbConverter.yuvToRgb(image);
                listener.onImageAvailable(bitmap);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }

    private final CameraManager.AvailabilityCallback cameraAvailabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(@NonNull String cameraId) {
            super.onCameraAvailable(cameraId);
            if (activeCameraId != null && activeCameraId.equals(cameraId) && activeSurface != null && cameraHandler != null) {
                Log.i(TAG, "Our active camera (" + cameraId + ") became available. Re-opening...");
                cameraHandler.post(() -> startCamera(activeSurface));
            }
        }

        @Override
        public void onCameraUnavailable(@NonNull String cameraId) {
            super.onCameraUnavailable(cameraId);
            if (activeCameraId != null && activeCameraId.equals(cameraId) && captureSession != null) {
                Log.w(TAG, "Our active camera (" + cameraId + ") became unavailable (likely used by another app).");
                try {
                    captureSession.close();
                } catch(Exception e) {
                    Log.e(TAG, "Error closing capture session on unavailable", e);
                }
            }
        }
    };

    private void createCaptureSession(Surface previewSurface) {
        if (isStopping || cameraDevice == null || previewSurface == null || !previewSurface.isValid() || imageReader == null) return;
        try {
            // **关键修正：在创建新会话之前，确保关闭任何可能存在的旧会话**
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }

            Surface imageReaderSurface = imageReader.getSurface();
            final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReaderSurface);
            builder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(Arrays.asList(imageReaderSurface, previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(settings.DESIRED_CAMERA_FPS, settings.DESIRED_CAMERA_FPS));
                        session.setRepeatingRequest(builder.build(), null, cameraHandler);
                        Log.d(TAG, "Capture session configured and repeating request set.");
                    } catch (CameraAccessException | IllegalStateException e) { // ** 关键修正：捕获IllegalStateException **
                        listener.onCameraError("Failed to start camera preview: " + e.getMessage());
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    listener.onCameraError("Failed to configure capture session.");
                    try { session.close(); } catch(Exception e) { /* ignore */ }
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            listener.onCameraError("Failed to create capture session: " + e.getMessage());
        }
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("CameraHelperThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback, cameraHandler);
    }

    private void stopCameraThread() {
        if (cameraManager != null) {
            cameraManager.unregisterAvailabilityCallback(cameraAvailabilityCallback);
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try {
                cameraThread.join();
                cameraThread = null;
                cameraHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping camera thread", e);
            }
        }
    }

    private String findCamera(int facing) throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            Integer cameraFacing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
            if (cameraFacing != null && cameraFacing == facing) return cameraId;
        }
        return null;
    }
}
