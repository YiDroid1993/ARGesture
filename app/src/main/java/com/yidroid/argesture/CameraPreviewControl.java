// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/CameraPreviewControl.java
// 描述: 负责管理两个TextureView以适应屏幕旋转的控制类。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

public class CameraPreviewControl {

    private final Context context;
    private final WindowManager windowManager;
    private final GestureSettings settings;

    private TextureView landscapePreview;
    private TextureView portraitPreview;
    private OverlayView overlayView;
    private TextureView activePreview;
    private final TextureView.SurfaceTextureListener listener;

    public CameraPreviewControl(Context context, TextureView.SurfaceTextureListener listener) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.settings = GestureSettings.getInstance(context);
        this.listener = listener;
    }

    public void show() {
        if (landscapePreview == null) {
            landscapePreview = new TextureView(context);
            landscapePreview.setSurfaceTextureListener(listener);
            windowManager.addView(landscapePreview, createLayoutParams(true));
        }

        if (portraitPreview == null) {
            portraitPreview = new TextureView(context);
            portraitPreview.setSurfaceTextureListener(listener);
            windowManager.addView(portraitPreview, createLayoutParams(false));
        }

        if (overlayView == null) {
            overlayView = new OverlayView(context, null);
            windowManager.addView(overlayView, createLayoutParams(isCurrentOrientationLandscape()));
        }

        onConfigurationChanged(); // Call to set initial visibility
    }

    public void hide() {
        if (landscapePreview != null) landscapePreview.setVisibility(View.GONE);
        if (portraitPreview != null) portraitPreview.setVisibility(View.GONE);
        if (overlayView != null) overlayView.setVisibility(View.GONE);
    }

    public void destroy() {
        if (landscapePreview != null && landscapePreview.isAttachedToWindow()) {
            windowManager.removeView(landscapePreview);
            landscapePreview = null;
        }
        if (portraitPreview != null && portraitPreview.isAttachedToWindow()) {
            windowManager.removeView(portraitPreview);
            portraitPreview = null;
        }
        if (overlayView != null && overlayView.isAttachedToWindow()) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        activePreview = null;
    }

    public void onConfigurationChanged() {
        if (landscapePreview == null || portraitPreview == null || overlayView == null) return;

        boolean isLandscape = isCurrentOrientationLandscape();

        windowManager.updateViewLayout(landscapePreview, createLayoutParams(true));
        windowManager.updateViewLayout(portraitPreview, createLayoutParams(false));
        windowManager.updateViewLayout(overlayView, createLayoutParams(isLandscape));

        if (isLandscape) {
            landscapePreview.setVisibility(View.VISIBLE);
            portraitPreview.setVisibility(View.GONE);
            activePreview = landscapePreview;
        } else {
            portraitPreview.setVisibility(View.VISIBLE);
            landscapePreview.setVisibility(View.GONE);
            activePreview = portraitPreview;
        }
        overlayView.setVisibility(View.VISIBLE);
    }

    public OverlayView getOverlayView() {
        return overlayView;
    }

    private boolean isCurrentOrientationLandscape(){
        return settings.SCREEN_ROTATION == Surface.ROTATION_90 || settings.SCREEN_ROTATION == Surface.ROTATION_270;
    }

    private WindowManager.LayoutParams createLayoutParams(boolean forLandscape) {
        int previewWidth, previewHeight;
        if (forLandscape) {
            previewWidth = settings.PREVIEW_WINDOW_WIDTH;
            previewHeight = settings.PREVIEW_WINDOW_HEIGHT;
        } else {
            previewWidth = settings.PREVIEW_WINDOW_HEIGHT;
            previewHeight = settings.PREVIEW_WINDOW_WIDTH;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                previewWidth,
                previewHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;
        params.alpha = settings.PREVIEW_WINDOW_ALPHA;
        return params;
    }
}
