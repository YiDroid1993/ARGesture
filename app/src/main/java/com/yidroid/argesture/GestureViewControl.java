// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureViewControl.java
// 描述: [已重构] 负责光标悬浮窗管理。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

public class GestureViewControl {

    private final Context context;
    private final WindowManager windowManager;
    private final GestureSettings settings;
    private View cursorView;

    public GestureViewControl(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.settings = GestureSettings.getInstance(context);
    }

    public void createCursor() {
        if (cursorView != null) return;
        cursorView = new View(context);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                40,
                40,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = settings.SCREEN_WIDTH / 2 - 20;
        params.y = settings.SCREEN_HEIGHT / 2 - 20;
        cursorView.setBackground(new CursorDrawable());
        windowManager.addView(cursorView, params);
        cursorView.setVisibility(View.VISIBLE);
    }

    public void removeCursor() {
        if (windowManager != null && cursorView != null && cursorView.isAttachedToWindow()) {
            windowManager.removeView(cursorView);
            cursorView = null;
        }
    }

    public void setCursorVisibility(boolean visible) {
        if (cursorView != null) {
            cursorView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void updateCursorPosition(int x, int y) {
        if (cursorView == null) return;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) cursorView.getLayoutParams();
        params.x = x - params.width / 2;
        params.y = y - params.height / 2;
        windowManager.updateViewLayout(cursorView, params);
    }

    private static class CursorDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint;
        CursorDrawable() {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
        }
        @Override public void draw(@NonNull Canvas canvas) { canvas.drawCircle(getBounds().exactCenterX(), getBounds().exactCenterY(), getBounds().width() / 2f - 5, paint); }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
