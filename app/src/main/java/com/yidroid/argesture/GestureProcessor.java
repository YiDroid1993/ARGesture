// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureProcessor.java
// 描述: [已重构] 手势处理类，实现了三指点击、画圈返回和坐标平滑。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.graphics.PointF;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.ArrayList;
import java.util.Collections;
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
    private String activeHand = "Right";
    private boolean isPinching = false;
    private long lastClickTime = 0;
    private boolean isFistClosed = false;
    private long lastHomeActionTime = 0;

    // --- 坐标平滑处理 ---
    /**
     * 平滑算法使用的歷史坐標點緩衝區大小。
     * 較大的值會更平滑，但延遲也更高。
     */
    private static final int SMOOTHING_BUFFER_SIZE = 7;
    private final List<Float> xHistory = new ArrayList<>();
    private final List<Float> yHistory = new ArrayList<>();

    // --- 畫圈手勢檢測 ---
    /**
     * 構成一個有效畫圈手勢所需的最少軌跡點數量。
     */
    private static final int CIRCLE_GESTURE_MIN_POINTS = 10;
    /**
     * 畫圈手勢的最小半徑（歸一化坐標）。
     * 用於過濾掉因手部輕微抖動產生的小圈。
     */
    private static final float CIRCLE_GESTURE_MIN_RADIUS = 0.05f;
    /**
     * 判斷畫圈手勢是否閉合的閾值。
     * 即軌跡的起點和終點的最大允許距離。
     */
    private static final float CIRCLE_GESTURE_COMPLETION_THRESHOLD = 0.05f;
    /**
     * 存儲食指運動軌跡的列表。
     */
    private final List<PointF> circlePath = new ArrayList<>();
    private long lastBackActionTime = 0;

    public GestureProcessor(Context context, GestureListener listener) {
        this.context = context;
        this.settings = GestureSettings.getInstance(context);
        this.listener = listener;
    }

    public void process(HandLandmarkerResult result, int imageWidth, int imageHeight) {
        if (result != null && !result.landmarks().isEmpty()) {
            checkForHandSwitch(result);
            List<NormalizedLandmark> landmarks = getActiveHandLandmarks(result);
            if (landmarks != null) {
                processGestures(landmarks, imageWidth, imageHeight);
                return;
            }
        }

        resetGestureStates();
        if (listener != null) {
            listener.onNoHandDetected();
        }
    }

    /**
     * 檢測用戶是否將手移動到屏幕邊緣，並智能切換主控手。
     */
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

    /**
     * 從識別結果中獲取當前主控手對應的關節點列表。
     */
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
        circlePath.clear();
    }

    /**
     * 主手勢處理邏輯。
     * @param landmarks 當前主控手的21個關節點。
     */
    private void processGestures(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        if (landmarks.size() < 21 || listener == null) return;

        // --- 1. 坐標平滑處理 ---
        float rawX = landmarks.get(8).x(); // 食指指尖原始X坐標
        float rawY = landmarks.get(8).y(); // 食指指尖原始Y坐標

        if (settings.ACTIVE_CAMERA_FACING == CameraCharacteristics.LENS_FACING_FRONT) {
            rawX = 1.0f - rawX;
        }

        PointF smoothedLandmark = getSmoothedLandmark(rawX, rawY);

        // --- ** 关键修正：重构坐标映射逻辑 ** ---
        float imageAspectRatio = (float) imageWidth / imageHeight;
        float screenAspectRatio = (float) settings.SCREEN_WIDTH / settings.SCREEN_HEIGHT;
        int mappedWidth, mappedHeight, offsetX = 0, offsetY = 0;

        if (imageAspectRatio > screenAspectRatio) {
            mappedWidth = settings.SCREEN_WIDTH;
            mappedHeight = (int) (settings.SCREEN_WIDTH / imageAspectRatio);
            offsetY = (settings.SCREEN_HEIGHT - mappedHeight) / 2;
        } else {
            mappedHeight = settings.SCREEN_HEIGHT;
            mappedWidth = (int) (settings.SCREEN_HEIGHT * imageAspectRatio);
            offsetX = (settings.SCREEN_WIDTH - mappedWidth) / 2;
        }

        int cursorX = (int) (offsetX + (smoothedLandmark.x * mappedWidth));
        int cursorY = (int) (offsetY + (smoothedLandmark.y * mappedHeight));

        listener.onUpdateCursor(cursorX, cursorY);

        // --- 2. 手勢檢測 ---
        if (detectBackHook(landmarks)) return; // 勾指返回优先
        if (detectIndexFingerUp(landmarks)) {
            processCircleGesture(smoothedLandmark);
            return;
        } else {
            circlePath.clear();
        }

        if (detectThreeFingerPinch(landmarks)) {
            if (!isPinching && (System.currentTimeMillis() - lastClickTime > settings.CLICK_DEBOUNCE)) {
                listener.onPerformClick(cursorX, cursorY);
                isPinching = true;
                lastClickTime = System.currentTimeMillis();
            }
        } else {
            isPinching = false;
        }

        if (detectFist(landmarks)) {
            if (!isFistClosed && (System.currentTimeMillis() - lastHomeActionTime > settings.HOME_DEBOUNCE)) {
                listener.onPerformHome();
                isFistClosed = true;
                lastHomeActionTime = System.currentTimeMillis();
            }
        } else {
            isFistClosed = false;
        }
    }

    /**
     * [新算法] 檢測三指捏合手勢（拇指、食指、中指）。
     * @return 如果三個指尖距離足夠近，返回 true。
     */
    private boolean detectThreeFingerPinch(List<NormalizedLandmark> landmarks) {
        double distThumbIndex = getDistance(landmarks.get(4), landmarks.get(8));
        double distThumbMiddle = getDistance(landmarks.get(4), landmarks.get(12));
        double distIndexMiddle = getDistance(landmarks.get(8), landmarks.get(12));

        return distThumbIndex < settings.PINCH_THRESHOLD &&
                distThumbMiddle < settings.PINCH_THRESHOLD &&
                distIndexMiddle < settings.PINCH_THRESHOLD;
    }

    /**
     * 檢測握拳手勢。
     * @return 如果四個手指的指尖都靠近手腕，返回 true。
     */
    private boolean detectFist(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark wrist = landmarks.get(0);
        return getDistance(landmarks.get(8), wrist) < settings.FIST_THRESHOLD &&
                getDistance(landmarks.get(12), wrist) < settings.FIST_THRESHOLD &&
                getDistance(landmarks.get(16), wrist) < settings.FIST_THRESHOLD &&
                getDistance(landmarks.get(20), wrist) < settings.FIST_THRESHOLD;
    }

    private boolean isBackGestureReady = false;

    /**
     * [新算法] 檢測食指、中指、無名指伸直後向手心勾的返回手勢。
     * @return 如果觸發了返回手勢，返回 true。
     */
    private boolean detectBackHook(List<NormalizedLandmark> landmarks) {
        // 判斷手指是否伸直：指尖Y坐標 < 第二關節Y坐標
        boolean indexStraight = landmarks.get(8).y() < landmarks.get(6).y();
        boolean middleStraight = landmarks.get(12).y() < landmarks.get(10).y();
        boolean ringStraight = landmarks.get(16).y() < landmarks.get(14).y();
        // 判斷小指和拇指是否彎曲
        boolean pinkyBent = landmarks.get(20).y() > landmarks.get(18).y();
        boolean thumbBent = landmarks.get(4).x() > landmarks.get(3).x(); // 簡單判斷拇指是否內收

        // 條件1：進入準備狀態
        if (indexStraight && middleStraight && ringStraight && pinkyBent && thumbBent) {
            isBackGestureReady = true;
        }

        // 條件2：從準備狀態，檢測到手指彎曲（觸發）
        if (isBackGestureReady) {
            // 判斷手指是否彎曲：指尖Y坐標 > 第一關節Y坐標
            boolean indexHooked = landmarks.get(8).y() > landmarks.get(5).y();
            boolean middleHooked = landmarks.get(12).y() > landmarks.get(9).y();
            boolean ringHooked = landmarks.get(16).y() > landmarks.get(13).y();

            if (indexHooked && middleHooked && ringHooked) {
                if (System.currentTimeMillis() - lastBackActionTime > settings.BACK_DEBOUNCE) {
                    listener.onPerformBack();
                    lastBackActionTime = System.currentTimeMillis();
                }
                isBackGestureReady = false; // 重置狀態
                return true; // 消耗此幀，不再檢測其他手勢
            }
        }

        // 如果手指不再伸直，則重置準備狀態
        if (!indexStraight || !middleStraight || !ringStraight) {
            isBackGestureReady = false;
        }

        return false;
    }

    /**
     * [新算法] 檢測是否為食指伸出、其餘四指彎曲的“畫圈準備”姿勢。
     * @return 如果滿足姿勢條件，返回 true。
     */
    private boolean detectIndexFingerUp(List<NormalizedLandmark> landmarks) {
        boolean indexStraight = landmarks.get(8).y() < landmarks.get(6).y();
        boolean middleBent = landmarks.get(12).y() > landmarks.get(10).y();
        boolean ringBent = landmarks.get(16).y() > landmarks.get(14).y();
        boolean pinkyBent = landmarks.get(20).y() > landmarks.get(18).y();
        boolean thumbBent = landmarks.get(4).x() > landmarks.get(3).x();

        return indexStraight && middleBent && ringBent && pinkyBent && thumbBent;
    }

    /**
     * [新算法] 處理畫圈手勢的軌跡記錄和分析。
     * @param currentPoint 當前食指指尖的平滑坐標。
     */
    private void processCircleGesture(PointF currentPoint) {
        if (System.currentTimeMillis() - lastBackActionTime < settings.BACK_DEBOUNCE) return;

        circlePath.add(currentPoint);

        if (circlePath.size() > CIRCLE_GESTURE_MIN_POINTS) {
            if (isPathACircle()) {
                listener.onPerformBack();
                lastBackActionTime = System.currentTimeMillis();
                circlePath.clear();
            }
        }

        if (circlePath.size() > 50) {
            circlePath.remove(0);
        }
    }

    /**
     * [新算法] 判斷存儲的軌跡是否構成一個圓圈。
     * @return 如果軌跡滿足圓圈的幾個基本特徵，返回 true。
     */
    private boolean isPathACircle() {
        if (circlePath.size() < CIRCLE_GESTURE_MIN_POINTS) return false;

        PointF startPoint = circlePath.get(0);
        PointF endPoint = circlePath.get(circlePath.size() - 1);

        float dx = startPoint.x - endPoint.x;
        float dy = startPoint.y - endPoint.y;
        if (Math.sqrt(dx*dx + dy*dy) > CIRCLE_GESTURE_COMPLETION_THRESHOLD) {
            return false;
        }

        float centerX = 0, centerY = 0;
        for (PointF p : circlePath) {
            centerX += p.x;
            centerY += p.y;
        }
        centerX /= circlePath.size();
        centerY /= circlePath.size();

        float totalRadius = 0;
        for (PointF p : circlePath) {
            totalRadius += Math.sqrt(Math.pow(p.x - centerX, 2) + Math.pow(p.y - centerY, 2));
        }
        float avgRadius = totalRadius / circlePath.size();

        return avgRadius > CIRCLE_GESTURE_MIN_RADIUS;
    }

    /**
     * [新算法] 對輸入的坐標進行中位數濾波，以獲得平滑的輸出。
     * @param newX 原始的X坐標。
     * @param newY 原始的Y坐標。
     * @return 經過平滑處理後的坐標點。
     */
    private PointF getSmoothedLandmark(float newX, float newY) {
        xHistory.add(newX);
        yHistory.add(newY);
        if (xHistory.size() > SMOOTHING_BUFFER_SIZE) {
            xHistory.remove(0);
            yHistory.remove(0);
        }

        if (xHistory.isEmpty()) {
            return new PointF(newX, newY);
        }

        List<Float> sortedX = new ArrayList<>(xHistory);
        List<Float> sortedY = new ArrayList<>(yHistory);
        Collections.sort(sortedX);
        Collections.sort(sortedY);

        float smoothedX = sortedX.get(sortedX.size() / 2);
        float smoothedY = sortedY.get(sortedY.size() / 2);

        return new PointF(smoothedX, smoothedY);
    }

    /**
     * 計算兩個3D關節點之間的歐氏距離。
     */
    private double getDistance(NormalizedLandmark p1, NormalizedLandmark p2) {
        return Math.sqrt(Math.pow(p1.x() - p2.x(), 2) + Math.pow(p1.y() - p2.y(), 2) + Math.pow(p1.z() - p2.z(), 2));
    }
}
