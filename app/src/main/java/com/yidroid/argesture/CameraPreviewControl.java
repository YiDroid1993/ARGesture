// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/CameraPreviewControl.java
// 描述: [新文件] 负责管理两个TextureView以适应屏幕旋转的控制类。
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
    private TextureView activePreview;
    private TextureView.SurfaceTextureListener listener;

    public CameraPreviewControl(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.settings = GestureSettings.getInstance(context);
    }

    public void show(TextureView.SurfaceTextureListener listener) {
        this.listener = listener;

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

        onConfigurationChanged(); // Call to set initial visibility
    }

    public void hide() {
        if (landscapePreview != null) landscapePreview.setVisibility(View.GONE);
        if (portraitPreview != null) portraitPreview.setVisibility(View.GONE);
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
        activePreview = null;
    }

    public void onConfigurationChanged() {
        if (landscapePreview == null || portraitPreview == null) return;

        boolean isLandscape = settings.SCREEN_ROTATION == Surface.ROTATION_90 || settings.SCREEN_ROTATION == Surface.ROTATION_270;

        windowManager.updateViewLayout(landscapePreview, createLayoutParams(true));
        windowManager.updateViewLayout(portraitPreview, createLayoutParams(false));

        if (isLandscape) {
            if (landscapePreview.getVisibility() != View.VISIBLE) {
                landscapePreview.setVisibility(View.VISIBLE);
            }
            if (portraitPreview.getVisibility() == View.VISIBLE) {
                portraitPreview.setVisibility(View.GONE);
            }
            activePreview = landscapePreview;
        } else {
            if (portraitPreview.getVisibility() != View.VISIBLE) {
                portraitPreview.setVisibility(View.VISIBLE);
            }
            if (landscapePreview.getVisibility() == View.VISIBLE) {
                landscapePreview.setVisibility(View.GONE);
            }
            activePreview = portraitPreview;
        }
    }

    private WindowManager.LayoutParams createLayoutParams(boolean forLandscape) {
        int previewWidth, previewHeight;
        if (forLandscape) {
            previewWidth = settings.PREVIEW_WINDOW_WIDTH;
            previewHeight = settings.PREVIEW_WINDOW_HEIGHT;
        } else {
            // For portrait, swap the dimensions
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
