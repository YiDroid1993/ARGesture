// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/CameraPreview.java
// 描述: [新文件] 自定义TextureView，用于处理预览画面的方向和变换。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CameraPreview extends TextureView {
    private static final String TAG = "CameraPreview";
    private int cameraSensorRotation = -1;
    private GestureSettings settings;

    public CameraPreview(@NonNull Context context) {
        this(context, null);
    }

    public CameraPreview(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraPreview(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.settings = GestureSettings.getInstance(context);
    }

    public void setCameraSensorRotation(int rotation) {
        this.cameraSensorRotation = rotation;
        // 在获取到传感器方向后，立即尝试更新变换
        //post(this::updateTransform);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged in custom view");
        // 配置发生变化（例如旋转），延迟更新变换以确保视图尺寸已更新
        //post(this::updateTransform);
    }

    /**
     * 更新TextureView的变换矩阵以校正相机预览的方向和宽高比。
     */
    public void updateTransform() {
        if (cameraSensorRotation == -1 || !isAvailable()) {
            return;
        }

        Matrix matrix = new Matrix();
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        int displayRotation = getDisplay().getRotation();
        int displayRotationDegrees = 0;
        switch (displayRotation) {
            case Surface.ROTATION_90: displayRotationDegrees = 90; break;
            case Surface.ROTATION_180: displayRotationDegrees = 180; break;
            case Surface.ROTATION_270: displayRotationDegrees = 270; break;
        }

        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect;

        // 相机流的原始尺寸 (通常是横向的)
        float cameraStreamWidth = settings.CAMERA_WIDTH;
        float cameraStreamHeight = settings.CAMERA_HEIGHT;

        // 根据传感器方向和屏幕方向的总旋转角度，判断相机画面的有效宽高
        int totalRotation = (cameraSensorRotation - displayRotationDegrees + 360) % 360;
        if (totalRotation == 90 || totalRotation == 270) {
            bufferRect = new RectF(0, 0, cameraStreamHeight, cameraStreamWidth);
        } else {
            bufferRect = new RectF(0, 0, cameraStreamWidth, cameraStreamHeight);
        }

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        // 计算缩放比例，使画面能完整显示在视图内
        matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER);

        // 再应用旋转
        matrix.postRotate(totalRotation, centerX, centerY);

        setTransform(matrix);
    }

    /*
     * 之前版本的变换逻辑，注释保留以供参考。
     * public void updatePreviewTransform_old(int cameraSensorRotation) {
     * if (cameraPreview == null) return;
     * Matrix matrix = new Matrix();
     * RectF viewRect = new RectF(0, 0, settings.PREVIEW_WINDOW_WIDTH, settings.PREVIEW_WINDOW_HEIGHT);
     * RectF bufferRect = new RectF(0, 0, settings.CAMERA_HEIGHT, settings.CAMERA_WIDTH);
     * float centerX = viewRect.centerX();
     * float centerY = viewRect.centerY();
     * int displayRotation = settings.SCREEN_ROTATION;
     * int degrees = (cameraSensorRotation - displayRotation * 90 + 360) % 360;
     * matrix.postRotate(-degrees, centerX, centerY);
     * bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
     * matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.FILL);
     * float scale = Math.max(
     * (float) settings.PREVIEW_WINDOW_HEIGHT / settings.CAMERA_HEIGHT,
     * (float) settings.PREVIEW_WINDOW_WIDTH / settings.CAMERA_WIDTH);
     * matrix.postScale(scale, scale, centerX, centerY);
     * cameraPreview.setTransform(matrix);
     * }
     */
}
