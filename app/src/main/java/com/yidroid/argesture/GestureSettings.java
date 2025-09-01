// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureSettings.java
// 描述: 设置管理类。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.provider.Settings.System;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public class GestureSettings {

    private static volatile GestureSettings instance;
    private final Context context;
    private final Point displaySize = new Point();

    // --- 系统值 ---
    public int SCREEN_WIDTH;
    public int SCREEN_HEIGHT;
    public int SCREEN_ROTATION;
    public long SCREEN_OFF_TIMEOUT;

    // --- 摄像头 ---
    public int CAMERA_WIDTH = 640;
    public int CAMERA_HEIGHT = 480;
    public int DESIRED_CAMERA_FPS = 24;
    public int ACTIVE_CAMERA_FACING;

    // --- 预览窗口 ---
    public int PREVIEW_WINDOW_WIDTH = 1440;
    public int PREVIEW_WINDOW_HEIGHT = 1080;
    public float PREVIEW_WINDOW_ALPHA = 0.3f;
    public int MAX_PREVIEW_WINDOW_WIDTH = 1440;
    public int AVOIDANCE_PREVIEW_WIDTH = 320;
    public int AVOIDANCE_PREVIEW_HEIGHT = 240;

    // --- 坐标映射 ---
    public int MAPPED_PREVIEW_WIDTH;
    public int HORIZONTAL_OFFSET;

    // --- 手势识别 ---
    public double PINCH_THRESHOLD = 0.04;
    public double FIST_THRESHOLD = 0.2;
    public long IDLE_TIMEOUT_MS = 60 * 1000; // 1分钟

    // --- 防抖 ---
    public long CLICK_DEBOUNCE = 1000;
    public long HOME_DEBOUNCE = 1000;
    public long BACK_DEBOUNCE = 1000;
    public long SCROLL_INTERVAL = 100;

    private GestureSettings(Context context) {
        this.context = context.getApplicationContext();
        loadSystemValues();
        calculateDependentValues();
    }

    public static GestureSettings getInstance(Context context) {
        if (instance == null) {
            synchronized (GestureSettings.class) {
                if (instance == null) {
                    instance = new GestureSettings(context);
                }
            }
        }
        return instance;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        loadSystemValues();
        calculateDependentValues();
    }

    private void loadSystemValues() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getRealSize(displaySize);
        SCREEN_ROTATION = display.getRotation();

        switch (SCREEN_ROTATION) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                SCREEN_WIDTH = Math.max(displaySize.x, displaySize.y);
                SCREEN_HEIGHT = Math.min(displaySize.x, displaySize.y);
                PREVIEW_WINDOW_HEIGHT = Math.min(displaySize.x, displaySize.y);
                PREVIEW_WINDOW_WIDTH = (PREVIEW_WINDOW_HEIGHT / 2) * 3;
                break;
            default: // ROTATION_0, ROTATION_180
                SCREEN_WIDTH = Math.min(displaySize.x, displaySize.y);
                SCREEN_HEIGHT = Math.max(displaySize.x, displaySize.y);
                PREVIEW_WINDOW_WIDTH = Math.min(displaySize.x, displaySize.y);
                PREVIEW_WINDOW_HEIGHT = (PREVIEW_WINDOW_WIDTH / 2) * 3;
                break;
        }

        try {
            SCREEN_OFF_TIMEOUT = System.getLong(context.getContentResolver(), System.SCREEN_OFF_TIMEOUT);
        } catch (Exception e) {
            SCREEN_OFF_TIMEOUT = IDLE_TIMEOUT_MS; // Fallback
        }
    }

    private void calculateDependentValues() {
        double cameraRatio = (double) CAMERA_WIDTH / CAMERA_HEIGHT;
        MAPPED_PREVIEW_WIDTH = (int) (SCREEN_HEIGHT * cameraRatio);
        HORIZONTAL_OFFSET = (SCREEN_WIDTH - MAPPED_PREVIEW_WIDTH) / 2;
    }
}
