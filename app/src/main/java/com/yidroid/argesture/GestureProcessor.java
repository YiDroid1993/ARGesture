// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureProcessor.java
// 描述: 手势处理类，已实现前置摄像头镜像修正。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import java.util.List;

public class GestureProcessor {

    public interface GestureListener {
        void onUpdateCursor(int x, int y);
        void onPerformClick(int x, int y);
        void onPerformScroll(int x, int y, int direction);
        void onPerformHome();
        void onPerformBack();
        void onNoHandDetected();
    }

    private final Context context;
    private final GestureListener listener;
    private final GestureSettings settings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Toast handSwitchToast;

    private String activeHand = "Right"; // 默认右手

    private boolean isPinching = false;
    private long lastClickTime = 0;
    private boolean isFistClosed = false;
    private long lastHomeActionTime = 0;

    public GestureProcessor(Context context, GestureListener listener) {
        this.context = context;
        this.settings = GestureSettings.getInstance(context);
        this.listener = listener;
    }

    public void process(HandLandmarkerResult result) {
        if (result != null && !result.landmarks().isEmpty()) {
            checkForHandSwitch(result);
            List<NormalizedLandmark> landmarks = getActiveHandLandmarks(result);
            if (landmarks != null) {
                processGestures(landmarks);
                return;
            }
        }

        resetGestureStates();
        if (listener != null) {
            listener.onNoHandDetected();
        }
    }

    private void checkForHandSwitch(HandLandmarkerResult result) {
        for (int i = 0; i < result.landmarks().size(); i++) {
            List<NormalizedLandmark> landmarks = result.landmarks().get(i);
            List<Category> handedness = result.handedness().get(i);
            if (landmarks.isEmpty() || handedness.isEmpty()) continue;

            String currentHand = handedness.get(0).categoryName();
            float handXPosition = landmarks.get(0).x();

            if (settings.ACTIVE_CAMERA_FACING == CameraCharacteristics.LENS_FACING_FRONT) {
                handXPosition = 1.0f - handXPosition;
            }

            if ("Right".equals(currentHand) && handXPosition > 0.7 && !"Left".equals(activeHand)) {
                showToast("右手处于边缘，切换至左手控制");
                activeHand = "Left";
            } else if ("Left".equals(currentHand) && handXPosition < 0.3 && !"Right".equals(activeHand)) {
                showToast("左手处于边缘，切换至右手控制");
                activeHand = "Right";
            }
        }
    }

    private List<NormalizedLandmark> getActiveHandLandmarks(HandLandmarkerResult result) {
        for (int i = 0; i < result.landmarks().size(); i++) {
            if (!result.handedness().get(i).isEmpty() &&
                    activeHand.equals(result.handedness().get(i).get(0).categoryName())) {
                return result.landmarks().get(i);
            }
        }
        return result.landmarks().isEmpty() ? null : result.landmarks().get(0);
    }

    private void showToast(String message) {
        mainHandler.post(() -> {
            if (handSwitchToast != null) {
                handSwitchToast.cancel();
            }
            handSwitchToast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            handSwitchToast.show();
        });
    }

    private void resetGestureStates() {
        isPinching = false;
        isFistClosed = false;
    }

    private void processGestures(List<NormalizedLandmark> landmarks) {
        if (landmarks.size() < 21 || listener == null) return;

        float landmarkX = landmarks.get(8).x();
        float landmarkY = landmarks.get(8).y();

        // ** 关键修正：前置摄像头镜像处理 **
        if (settings.ACTIVE_CAMERA_FACING == CameraCharacteristics.LENS_FACING_FRONT) {
            landmarkX = 1.0f - landmarkX;
        }

        int cursorX = settings.HORIZONTAL_OFFSET + (int) (landmarkX * settings.MAPPED_PREVIEW_WIDTH);
        int cursorY = (int) (landmarkY * settings.SCREEN_HEIGHT);

        listener.onUpdateCursor(cursorX, cursorY);

        if (detectPinch(landmarks)) {
            if (!isPinching && (System.currentTimeMillis() - lastClickTime > settings.CLICK_DEBOUNCE)) {
                listener.onPerformClick(cursorX, cursorY); isPinching = true; lastClickTime = System.currentTimeMillis();
            }
        } else { isPinching = false; }

        if (detectFist(landmarks)) {
            if (!isFistClosed && (System.currentTimeMillis() - lastHomeActionTime > settings.HOME_DEBOUNCE)) {
                listener.onPerformHome(); isFistClosed = true; lastHomeActionTime = System.currentTimeMillis();
            }
        } else { isFistClosed = false; }
    }

    private boolean detectPinch(List<NormalizedLandmark> landmarks) {
        return getDistance(landmarks.get(4), landmarks.get(8)) < settings.PINCH_THRESHOLD;
    }

    private boolean detectFist(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark wrist = landmarks.get(0);
        return getDistance(landmarks.get(8), wrist) < settings.FIST_THRESHOLD &&
                getDistance(landmarks.get(12), wrist) < settings.FIST_THRESHOLD &&
                getDistance(landmarks.get(16), wrist) < settings.FIST_THRESHOLD &&
                getDistance(landmarks.get(20), wrist) < settings.FIST_THRESHOLD;
    }

    private double getDistance(NormalizedLandmark p1, NormalizedLandmark p2) {
        return Math.sqrt(Math.pow(p1.x() - p2.x(), 2) + Math.pow(p1.y() - p2.y(), 2) + Math.pow(p1.z() - p2.z(), 2));
    }
}
