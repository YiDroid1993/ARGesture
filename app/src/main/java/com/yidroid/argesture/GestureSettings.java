// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureSettings.java
// 描述: [已重构] 设置管理类，添加了大量注释并修正预览尺寸逻辑。
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
    /**
     * 当前屏幕的实际宽度（像素）。
     * 在设备旋转时会自动更新。
     */
    public int SCREEN_WIDTH;
    /**
     * 当前屏幕的实际高度（像素）。
     * 在设备旋转时会自动更新。
     */
    public int SCREEN_HEIGHT;
    /**
     * 当前屏幕的旋转角度。
     * (Surface.ROTATION_0, Surface.ROTATION_90, etc.)
     */
    public int SCREEN_ROTATION;
    /**
     * 从系统设置中读取的屏幕自动熄灭超时时间（毫秒）。
     */
    public long SCREEN_OFF_TIMEOUT;

    // --- 摄像头 ---
    /**
     * 用于图像分析的摄像头画面宽度。
     * 较低的分辨率可以提升性能。
     */
    public int CAMERA_WIDTH = 640;
    /**
     * 用于图像分析的摄像头画面高度。
     */
    public int CAMERA_HEIGHT = 480;
    /**
     * 期望的摄像头帧率(FPS)。
     * 较低的帧率可以降低功耗和发热。
     */
    public int DESIRED_CAMERA_FPS = 24;
    /**
     * 当前正在使用的摄像头朝向。
     * (CameraCharacteristics.LENS_FACING_FRONT or LENS_FACING_BACK)
     */
    public int ACTIVE_CAMERA_FACING;

    // --- 预览窗口 ---
    /**
     * 预览悬浮窗的宽度（横屏状态下）。
     * 在竖屏时，此值会与高度互换。
     */
    public int PREVIEW_WINDOW_WIDTH = 1440;
    /**
     * 预览悬浮窗的高度（横屏状态下）。
     */
    public int PREVIEW_WINDOW_HEIGHT = 1080;
    /**
     * 预览悬浮窗的透明度。
     * 0.0f (完全透明) to 1.0f (完全不透明).
     */
    public float PREVIEW_WINDOW_ALPHA = 0.3f;
    /**
     * 用于判断是否启用窗口避让逻辑的最大宽度阈值。
     */
    public int MAX_PREVIEW_WINDOW_WIDTH = 1440;
    /**
     * 触发避让逻辑时，预览窗口的宽度。
     */
    public int AVOIDANCE_PREVIEW_WIDTH = 320;
    /**
     * 触发避让逻辑时，预览窗口的高度。
     */
    public int AVOIDANCE_PREVIEW_HEIGHT = 240;

    // --- 坐标映射 ---
    /**
     * 根据屏幕和相机比例计算出的，手势有效区域在屏幕上的映射宽度。
     */
    public int MAPPED_PREVIEW_WIDTH;
    /**
     * 手势有效区域在屏幕上的水平偏移量，用于居中显示。
     */
    public int HORIZONTAL_OFFSET;

    // --- 手势识别 ---
    /**
     * 判断为“捏合”手势时，指尖之间的最大距离阈值。
     * 值越小，要求捏得越近。
     */
    public double PINCH_THRESHOLD = 0.06; // 稍微放宽三指的阈值
    /**
     * 判断为“握拳”手势时，指尖到手腕的最大距离阈值。
     */
    public double FIST_THRESHOLD = 0.2;
    /**
     * 无手势活动时，自动停止服务的超时时间（毫秒）。
     */
    public long IDLE_TIMEOUT_MS = 60 * 1000; // 1分钟

    // --- 防抖 ---
    /**
     * 两次“点击”操作之间的最小间隔时间（毫秒），用于防止误触。
     */
    public long CLICK_DEBOUNCE = 1000;
    /**
     * 两次“返回桌面”操作之间的最小间隔时间（毫秒）。
     */
    public long HOME_DEBOUNCE = 1000;
    /**
     * 两次“返回”操作之间的最小间隔时间（毫秒）。
     */
    public long BACK_DEBOUNCE = 1000;
    /**
     * 两次“滚动”操作之间的最小间隔时间（毫秒）。
     */
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
