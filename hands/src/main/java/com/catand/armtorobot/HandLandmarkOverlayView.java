package com.catand.armtorobot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.Connection;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;

/**
 * 在预览画面上叠加绘制手部关键点与骨架连线。
 *
 * <p>绘制坐标系与 {@code PreviewView} 的 FIT_CENTER 保持一致：按输入图像宽高比居中留边，
 * 因此关键点与预览画面严格对齐。前置摄像头镜像时同步镜像叠加层。
 */
public class HandLandmarkOverlayView extends View {

	private static final int LEFT_HAND_CONNECTION_COLOR = Color.rgb(51, 255, 255);
	private static final int RIGHT_HAND_CONNECTION_COLOR = Color.rgb(255, 51, 255);
	private static final int LEFT_HAND_LANDMARK_COLOR = Color.rgb(255, 51, 51);
	private static final int RIGHT_HAND_LANDMARK_COLOR = Color.rgb(51, 255, 51);
	private static final int LEFT_HAND_HOLLOW_CIRCLE_COLOR = Color.rgb(51, 255, 51);
	private static final int RIGHT_HAND_HOLLOW_CIRCLE_COLOR = Color.rgb(255, 51, 51);

	/**
	 * 关键点半径，为归一化长度，按绘制区域短边换算
	 */
	private static final float LANDMARK_RADIUS = 0.008f;
	private static final float HOLLOW_CIRCLE_RADIUS = 0.01f;
	private static final float CONNECTION_THICKNESS = 3f;

	private final Paint connectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint landmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint hollowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	private HandLandmarkerResult result;
	/**
	 * 输入图像宽高比（已计入旋转）
	 */
	private float sourceAspectRatio = 4f / 3f;
	private boolean mirrored;

	public HandLandmarkOverlayView(Context context) {
		this(context, null);
	}

	public HandLandmarkOverlayView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public HandLandmarkOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		connectionPaint.setStyle(Paint.Style.STROKE);
		connectionPaint.setStrokeWidth(CONNECTION_THICKNESS);
		connectionPaint.setStrokeCap(Paint.Cap.ROUND);
		landmarkPaint.setStyle(Paint.Style.FILL);
		hollowPaint.setStyle(Paint.Style.STROKE);
		hollowPaint.setStrokeWidth(CONNECTION_THICKNESS);
	}

	public void setResult(@Nullable HandLandmarkerResult result) {
		this.result = result;
		invalidate();
	}

	/**
	 * 可从任意线程调用，宽度无法解析时忽略.
	 *
	 * @param width  输入图像旋转后宽度
	 * @param height 输入图像旋转后高度
	 */
	public void setSourceSize(int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		final float ratio = (float) width / height;
		if (Looper.myLooper() == Looper.getMainLooper()) {
			sourceAspectRatio = ratio;
		} else {
			post(() -> sourceAspectRatio = ratio);
		}
	}

	public void setMirrored(boolean mirrored) {
		this.mirrored = mirrored;
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		if (result == null || result.landmarks().isEmpty()) {
			return;
		}

		//按 FIT_CENTER 计算画面实际绘制区域
		final float viewWidth = getWidth();
		final float viewHeight = getHeight();
		float drawWidth = viewWidth;
		float drawHeight = drawWidth / sourceAspectRatio;
		if (drawHeight > viewHeight) {
			drawHeight = viewHeight;
			drawWidth = drawHeight * sourceAspectRatio;
		}
		final float offsetX = (viewWidth - drawWidth) / 2f;
		final float offsetY = (viewHeight - drawHeight) / 2f;
		final float radiusScale = Math.min(drawWidth, drawHeight);
		final float landmarkRadius = LANDMARK_RADIUS * radiusScale;
		final float hollowRadius = HOLLOW_CIRCLE_RADIUS * radiusScale;

		List<List<NormalizedLandmark>> hands = result.landmarks();
		for (int i = 0; i < hands.size(); i++) {
			boolean isLeftHand = isLeftHand(i);
			List<NormalizedLandmark> landmarks = hands.get(i);

			connectionPaint.setColor(isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);
			for (Connection connection : HandLandmarker.HAND_CONNECTIONS) {
				NormalizedLandmark start = landmarks.get(connection.start());
				NormalizedLandmark end = landmarks.get(connection.end());
				canvas.drawLine(
						mapX(start.x(), offsetX, drawWidth),
						mapY(start.y(), offsetY, drawHeight),
						mapX(end.x(), offsetX, drawWidth),
						mapY(end.y(), offsetY, drawHeight),
						connectionPaint);
			}

			for (NormalizedLandmark landmark : landmarks) {
				float x = mapX(landmark.x(), offsetX, drawWidth);
				float y = mapY(landmark.y(), offsetY, drawHeight);
				landmarkPaint.setColor(isLeftHand ? LEFT_HAND_LANDMARK_COLOR : RIGHT_HAND_LANDMARK_COLOR);
				canvas.drawCircle(x, y, landmarkRadius, landmarkPaint);
				hollowPaint.setColor(isLeftHand ? LEFT_HAND_HOLLOW_CIRCLE_COLOR : RIGHT_HAND_HOLLOW_CIRCLE_COLOR);
				canvas.drawCircle(x, y, hollowRadius, hollowPaint);
			}
		}
	}

	private boolean isLeftHand(int index) {
		List<List<com.google.mediapipe.tasks.components.containers.Category>> handedness =
				result.handedness();
		if (index >= handedness.size() || handedness.get(index).isEmpty()) {
			return false;
		}
		return "Left".equals(handedness.get(index).get(0).categoryName());
	}

	private float mapX(float normalizedX, float offsetX, float drawWidth) {
		return offsetX + (mirrored ? (1f - normalizedX) : normalizedX) * drawWidth;
	}

	private float mapY(float normalizedY, float offsetY, float drawHeight) {
		return offsetY + normalizedY * drawHeight;
	}
}
