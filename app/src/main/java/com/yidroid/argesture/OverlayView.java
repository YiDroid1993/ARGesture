// =================================================================================
// 文件: app/src/main/java/com/yidroid/argesture/OverlayView.java
// 描述: 用于在预览画面上绘制手势关节点和骨骼的自定义视图，并增加了前置摄像头镜像处理。
// =================================================================================
package com.yidroid.argesture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.camera2.CameraCharacteristics;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.Set;

public class OverlayView extends View {

    private HandLandmarkerResult results;
    private final Paint pointPaint;
    private final Paint linePaint;
    private int imageWidth;
    private int imageHeight;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        pointPaint = new Paint();
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setStrokeWidth(8f);

        linePaint = new Paint();
        linePaint.setColor(Color.GRAY);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
    }

    public void setResults(HandLandmarkerResult results, int imageWidth, int imageHeight, int cameraFacing) {
        this.results = results;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.cameraFacing = cameraFacing;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results == null || results.landmarks().isEmpty()) {
            return;
        }

        canvas.save();

        // ** 关键修正：如果是前置摄像头，则水平翻转画布 **
        if (cameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            canvas.scale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
        }

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (List<NormalizedLandmark> landmarks : results.landmarks()) {
            // Draw points
            for (NormalizedLandmark landmark : landmarks) {
                canvas.drawPoint(landmark.x() * imageWidth * scaleX, landmark.y() * imageHeight * scaleY, pointPaint);
            }
            // Draw connections
            Set<Connection> connections = HandLandmarker.HAND_CONNECTIONS;
            for (Connection c : connections) {
                NormalizedLandmark start = landmarks.get(c.start());
                NormalizedLandmark end = landmarks.get(c.end());
                canvas.drawLine(
                        start.x() * imageWidth * scaleX,
                        start.y() * imageHeight * scaleY,
                        end.x() * imageWidth * scaleX,
                        end.y() * imageHeight * scaleY,
                        linePaint);
            }
        }

        canvas.restore();
    }
}
