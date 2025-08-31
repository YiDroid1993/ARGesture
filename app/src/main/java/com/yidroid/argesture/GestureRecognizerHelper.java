// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/GestureRecognizerHelper.java
// 描述: MediaPipe帮助类，修正Delegate的包导入。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

public class GestureRecognizerHelper {
    public static final String TAG = "GestureRecognizerHelper";
    private static final String HAND_LANDMARKER_TASK_FILE = "hand_landmarker.task";

    private HandLandmarker handLandmarker;
    private final ResultListener listener;
    private final Context context;

    public GestureRecognizerHelper(Context context, ResultListener listener) {
        this.context = context;
        this.listener = listener;
        setupHandLandmarker();
    }

    private void setupHandLandmarker() {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(HAND_LANDMARKER_TASK_FILE)
                    .setDelegate(Delegate.GPU)
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setNumHands(2)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
                    .build();
            handLandmarker = HandLandmarker.createFromOptions(context, options);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up HandLandmarker on GPU, fallback to CPU: " + e.getMessage());
            try {
                BaseOptions baseOptions = BaseOptions.builder()
                        .setModelAssetPath(HAND_LANDMARKER_TASK_FILE)
                        .setDelegate(Delegate.CPU)
                        .build();
                HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setNumHands(2)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener(this::returnLivestreamResult)
                        .setErrorListener(this::returnLivestreamError)
                        .build();
                handLandmarker = HandLandmarker.createFromOptions(context, options);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to initialize even on CPU: " + ex.getMessage());
                if (listener != null) {
                    listener.onError("HandLandmarker setup failed on both GPU and CPU.");
                }
            }
        }
    }

    public void recognizeLiveStream(Bitmap bitmap) {
        if (handLandmarker == null) {
            return;
        }
        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
        handLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis());
    }

    private void returnLivestreamResult(HandLandmarkerResult result, MPImage input) {
        if (listener != null) {
            listener.onResults(result);
        }
    }

    private void returnLivestreamError(RuntimeException error) {
        if (listener != null) {
            listener.onError(error.getMessage());
        }
    }

    public void close() {
        if (handLandmarker != null) {
            handLandmarker.close();
            handLandmarker = null;
        }
    }

    public interface ResultListener {
        void onResults(HandLandmarkerResult result);
        void onError(String error);
    }
}
