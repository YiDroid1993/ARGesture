// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureAccessibilityService.java
// 描述: [已重构] 核心服务类，更新了与OverlayView的交互。
// =================================================================================
package com.yidroid.argesture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.concurrent.atomic.AtomicBoolean;

public class GestureAccessibilityService extends AccessibilityService
        implements GestureRecognizerHelper.ResultListener, GestureProcessor.GestureListener, CameraHelper.CameraListener {

    private static final String TAG = "GestureService";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "GestureServiceChannel";
    private static final String ACTION_TOGGLE_PREVIEW = "com.yidroid.argesture.TOGGLE_PREVIEW";

    private GestureSettings settings;
    private CursorControl cursorControl;
    private CameraPreviewControl previewControl;
    private GestureProcessor gestureProcessor;
    private GestureRecognizerHelper gestureRecognizerHelper;
    private CameraHelper cameraHelper;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int cameraSensorRotation = -1;
    private int rotatedImageWidth, rotatedImageHeight;

    private AtomicBoolean isGestureControlActive = new AtomicBoolean(false);
    private boolean isPreviewVisible = false;

    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleRunnable = this::handleIdleTimeout;

    private final BroadcastReceiver previewControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TOGGLE_PREVIEW.equals(intent.getAction())) {
                togglePreviewState();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service Connected");

        settings = GestureSettings.getInstance(this);
        cursorControl = new CursorControl(this);
        previewControl = new CameraPreviewControl(this, surfaceTextureListener);
        gestureProcessor = new GestureProcessor(this, this);
        cameraHelper = new CameraHelper(this, this);

        ContextCompat.registerReceiver(this, previewControlReceiver, new IntentFilter(ACTION_TOGGLE_PREVIEW), ContextCompat.RECEIVER_EXPORTED);

        startGestureControl();
        mainHandler.post(this::togglePreviewState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (settings != null) {
            settings.onConfigurationChanged(newConfig);
        }
        if (previewControl != null) {
            previewControl.onConfigurationChanged();
        }
    }

    private void togglePreviewState() {
        if (isPreviewVisible) {
            previewControl.hide();
            cameraHelper.stopCamera();
            isPreviewVisible = false;
        } else {
            previewControl.show();
            isPreviewVisible = true;
        }
        updateNotification();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            if (isGestureControlActive.get()) {
                cameraHelper.startCamera(new Surface(surfaceTexture));
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            cameraHelper.stop();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    private void startGestureControl() {
        if (isGestureControlActive.getAndSet(true)) return;
        Log.d(TAG, "Starting gesture control...");

        gestureRecognizerHelper = new GestureRecognizerHelper(this, this);
        cursorControl.create();
        cameraHelper.start();

        resetIdleTimer();
        updateNotification();
    }

    private void stopGestureControl() {
        if (!isGestureControlActive.getAndSet(false)) return;
        Log.d(TAG, "Stopping gesture control...");

        stopIdleTimer();
        cameraHelper.stop();
        if (gestureRecognizerHelper != null) {
            gestureRecognizerHelper.close();
            gestureRecognizerHelper = null;
        }
        cursorControl.destroy();
        previewControl.destroy();

        updateNotification();
    }

    private void handleIdleTimeout() {
        if (isGestureControlActive.get()) {
            Log.i(TAG, "Idle timeout reached. Stopping gesture control to save power.");
            Toast.makeText(this, "因长时间未使用，手势识别已暂停", Toast.LENGTH_SHORT).show();
            stopGestureControl();
        }
    }

    private void resetIdleTimer() {
        stopIdleTimer();
        idleHandler.postDelayed(idleRunnable, settings.IDLE_TIMEOUT_MS);
    }

    private void stopIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
    }

    private void updateNotification() {
        String contentText = isPreviewVisible ? "预览已开启，点击关闭预览" : "手势服务运行中，点击开启预览";
        NotificationManager manager = getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "AR手势服务", NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(ACTION_TOGGLE_PREVIEW);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("AR手势")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopGestureControl();
        unregisterReceiver(previewControlReceiver);
        stopForeground(true);
    }

    // --- Listeners Implementation ---
    @Override
    public void onResults(HandLandmarkerResult result) {
        mainHandler.post(() -> {
            if (isGestureControlActive.get()) {
                gestureProcessor.process(result, rotatedImageWidth, rotatedImageHeight);
                if (previewControl != null && isPreviewVisible) {
                    previewControl.getOverlayView().setResults(result, rotatedImageWidth, rotatedImageHeight, settings.ACTIVE_CAMERA_FACING);
                }
            }
        });
    }

    @Override
    public void onImageAvailable(Bitmap originalBitmap) {
        if (!isGestureControlActive.get() || gestureRecognizerHelper == null) return;
        resetIdleTimer();

        Matrix matrix = new Matrix();
        int screenRotationDegrees = 0;
        switch (settings.SCREEN_ROTATION) {
            case Surface.ROTATION_90: screenRotationDegrees = 90; break;
            case Surface.ROTATION_180: screenRotationDegrees = 180; break;
            case Surface.ROTATION_270: screenRotationDegrees = 270; break;
        }
        int rotationDegrees = (cameraSensorRotation - screenRotationDegrees + 360) % 360;
        matrix.postRotate(rotationDegrees);

        Bitmap rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
        rotatedImageWidth = rotatedBitmap.getWidth();
        rotatedImageHeight = rotatedBitmap.getHeight();
        gestureRecognizerHelper.recognizeLiveStream(rotatedBitmap);
    }

    @Override
    public void onCameraConfigured(String cameraId, int sensorRotation, int facing) {
        this.cameraSensorRotation = sensorRotation;
        this.settings.ACTIVE_CAMERA_FACING = facing;
    }

    @Override
    public void onCameraError(String message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override public void onError(String error) { Log.e(TAG, "Gesture Recognition Error: " + error); }
    @Override public void onUpdateCursor(int x, int y) { cursorControl.setVisibility(true); cursorControl.updatePosition(x, y); }
    @Override public void onPerformClick(int x, int y) {
        if (x < 0 || y < 0 || x > settings.SCREEN_WIDTH || y > settings.SCREEN_HEIGHT) return;
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100)).build(), null, null);
    }
    @Override public void onPerformScroll(int x, int y, int direction) { } // Not implemented
    @Override public void onPerformHome() { performGlobalAction(GLOBAL_ACTION_HOME); }
    @Override public void onPerformBack() { performGlobalAction(GLOBAL_ACTION_BACK); }
    @Override public void onNoHandDetected() {
        cursorControl.setVisibility(false);
        if (previewControl != null && isPreviewVisible) {
            previewControl.getOverlayView().setResults(null, 0, 0, settings.ACTIVE_CAMERA_FACING);
        }
    }

    // --- System Callbacks ---
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { Log.d(TAG, "Accessibility Service Interrupted"); }
}

