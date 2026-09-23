// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.catand.armtorobot;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.catand.armtorobot.commen.Constants;
import com.catand.armtorobot.connect.BLEManager;
import com.catand.armtorobot.connect.BLEService;
import com.catand.armtorobot.model.ByteCommand;
import com.catand.armtorobot.model.ServoAction;
import com.catand.armtorobot.uitls.BluetoothUtils;
import com.catand.armtorobot.uitls.CmdUtil;
import com.catand.armtorobot.uitls.LandmarkUtil;
import com.catand.armtorobot.uitls.LogUtil;
import com.catand.armtorobot.uitls.PermissionHelperBluetooth;
import com.catand.armtorobot.widget.NetworkDialog;
import com.catand.armtorobot.widget.PromptDialog;
import com.catand.armtorobot.widget.SearchDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmark;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用程序的主Activity.
 */
public class MainActivity extends AppCompatActivity implements SearchDialog.OnDeviceSelectedListener, NetworkDialog.OnRobotSelectedListener {
	private static final String TAG = MainActivity.class.getSimpleName();

	/**
	 * 手部关键点模型资源路径
	 */
	private static final String HAND_LANDMARKER_MODEL = "hand_landmarker.task";
	/**
	 * 最大识别手部数量
	 */
	private static final int HANDS_NUM = 1;
	/**
	 * 是否优先使用 GPU 委托跑模型推理，默认开启。
	 *
	 * <p>GPU 委托依赖设备驱动的 OpenGL/EGL 实现，个别设备上会初始化失败。失败分两类：
	 * 一类抛 Java 异常，当场就能 catch 住换 CPU；另一类在 native 层 CHECK 失败直接
	 * abort 整个进程，Java 侧完全拦不住。
	 *
	 * <p>所以回退状态记在 SharedPreferences 里：开跑前先落一个"GPU 尝试中"标记，
	 * 真跑出结果再清掉。若下次启动发现标记还在，说明上次是进程直接死掉了，
	 * 于是永久改用 CPU，不再反复闪退。
	 */
	private static final boolean RUN_ON_GPU = true;
	/**
	 * 回退状态存储
	 */
	private static final String PREF_NAME = "delegate";
	/**
	 * 上次 GPU 尝试未跑出结果（进程可能已 abort）
	 */
	private static final String PREF_GPU_PENDING = "gpu_pending";
	/**
	 * GPU 已被判定不可用，永久改用 CPU
	 */
	private static final String PREF_GPU_DISABLED = "gpu_disabled";

	private HandLandmarker handLandmarker;
	private PreviewView previewView;
	private HandLandmarkOverlayView landmarkOverlay;
	private ProcessCameraProvider cameraProvider;
	private ExecutorService analysisExecutor;
	/**
	 * 当前使用前置摄像头，用于镜像叠加层
	 */
	private boolean usingFrontCamera = true;
	/**
	 * detectAsync 要求时间戳严格递增
	 */
	private long lastFrameTimestampMs;

	//蓝牙重试次数
	private static final int RETRY_TIMES = 3;
	//确认退出
	private boolean confirm;
	//蓝牙按钮图像
	private ImageButton btStateBtn;
	//蓝牙适配器
	public static BluetoothAdapter mBluetoothAdapter = null;
	public static BLEManager bleManager;
	public static BluetoothDevice mBluetoothDevice;
	private Handler mHandler;
	/**
	 * 已尝试连接次数
	 */
	private int connectTimes;
	/**
	 * 蓝牙连接状态
	 */
	public static boolean bluetoothIsConnected;
	/**
	 * 服务器连接状态
	 */
	public static boolean networkIsConnected;
	private Handler handler = new Handler(Looper.getMainLooper());
	private OnBackPressedCallback onBackPressedCallback;

	//通用方法

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		previewView = findViewById(R.id.preview_view);
		previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
		landmarkOverlay = findViewById(R.id.landmark_overlay);
		applyWindowInsets();

		//初始化按钮点击事件监听器
		setupCameraClick();
		setupBluetoothClick();
		setupSetClick();
		setupTestClick();
		setupNetworkClick();
		setupBackPressedHandler();

		mBluetoothAdapter = BluetoothUtils.getAdapter(this);
		if (!BluetoothUtils.isSupport(mBluetoothAdapter)) {
			Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		//重置连接尝试次数
		connectTimes = 0;
		//创建蓝牙按钮对象
		btStateBtn = findViewById(R.id.bluetooth_btn);
		Intent intent = new Intent(this, BLEService.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		BLEManager.getInstance().register(this);
		mHandler = new Handler(new MsgCallBack());

		setupHandLandmarker();
	}

	/**
	 * targetSdk 35+ 在 Android 15 及以上强制全屏边到边，按钮栏需自行避让状态栏与导航栏.
	 */
	private void applyWindowInsets() {
		View buttons = findViewById(R.id.buttons);
		final int basePaddingTop = buttons.getPaddingTop();
		final int basePaddingBottom = buttons.getPaddingBottom();
		ViewCompat.setOnApplyWindowInsetsListener(buttons, (view, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			view.setPadding(
					view.getPaddingLeft(),
					basePaddingTop + bars.top,
					view.getPaddingRight(),
					basePaddingBottom + bars.bottom);
			return insets;
		});
	}

	/**
	 * 本机是否还能用 GPU 委托。上次若因 GPU 死在 native 层，这里会返回 false。
	 */
	private boolean shouldTryGpu() {
		if (!RUN_ON_GPU) {
			return false;
		}
		SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
		if (pref.getBoolean(PREF_GPU_DISABLED, false)) {
			LogUtil.i(TAG, "GPU 委托此前已判定不可用，直接用 CPU");
			return false;
		}
		//上次标记还在，说明上次没能跑出结果就没了，判定 GPU 不可用
		if (pref.getBoolean(PREF_GPU_PENDING, false)) {
			LogUtil.w(TAG, "上次 GPU 运行未跑出结果，判定不可用并改用 CPU");
			pref.edit().putBoolean(PREF_GPU_DISABLED, true).putBoolean(PREF_GPU_PENDING, false).apply();
			return false;
		}
		return true;
	}

	private void setupHandLandmarker() {
		boolean useGpu = shouldTryGpu();
		if (useGpu) {
			//先落标记，只有真正跑出一帧结果才会清掉
			getSharedPreferences(PREF_NAME, MODE_PRIVATE)
					.edit().putBoolean(PREF_GPU_PENDING, true).apply();
		}

		try {
			handLandmarker = createHandLandmarker(useGpu);
		} catch (RuntimeException e) {
			//这类失败抛的是 Java 异常，当场就能换 CPU
			Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e);
			if (useGpu) {
				getSharedPreferences(PREF_NAME, MODE_PRIVATE)
						.edit().putBoolean(PREF_GPU_DISABLED, true).putBoolean(PREF_GPU_PENDING, false).apply();
			}
			handLandmarker = createHandLandmarker(false);
		}
	}

	private HandLandmarker createHandLandmarker(boolean useGpu) {
		return HandLandmarker.createFromOptions(this,
				HandLandmarker.HandLandmarkerOptions.builder()
						.setBaseOptions(
								BaseOptions.builder()
										.setModelAssetPath(HAND_LANDMARKER_MODEL)
										.setDelegate(useGpu ? Delegate.GPU : Delegate.CPU)
										.build())
						.setRunningMode(RunningMode.LIVE_STREAM)
						.setNumHands(HANDS_NUM)
						.setMinHandDetectionConfidence(0.5f)
						.setMinHandPresenceConfidence(0.5f)
						.setMinTrackingConfidence(0.5f)
						.setErrorListener(e -> Log.e(TAG, "MediaPipe HandLandmarker error", e))
						.setResultListener(this::onHandLandmarkerResult)
						.build());
	}

	/**
	 * 结果回调运行在 MediaPipe 的工作线程上.
	 */
	private void onHandLandmarkerResult(HandLandmarkerResult result, MPImage inputImage) {
		try {
			//能跑到这里说明 GPU 通路真的活下来了，撤掉回退标记
			clearGpuPending();
			processWristLandmark(result);
			runOnUiThread(() -> landmarkOverlay.setResult(result));
		} finally {
			inputImage.close();
		}
	}

	/**
	 * GPU 已确认可用，撤掉"尝试中"标记.
	 */
	private void clearGpuPending() {
		SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
		if (pref.getBoolean(PREF_GPU_PENDING, false)) {
			pref.edit().putBoolean(PREF_GPU_PENDING, false).apply();
		}
	}

	//当应用恢复前台运行
	@Override
	protected void onResume() {
		super.onResume();

		//相机
		startCamera();
		landmarkOverlay.setVisibility(android.view.View.VISIBLE);

		//蓝牙
		bleManager = BLEManager.getInstance();
		bluetoothIsConnected = bleManager.isConnected();
		bleManager.setHandler(mHandler);
		LogUtil.i(TAG, "onResume isConnected= " + bluetoothIsConnected);
		setState(bluetoothIsConnected);
	}

	//当应用进入后台
	@Override
	protected void onPause() {
		super.onPause();
		stopCamera();
		landmarkOverlay.setVisibility(android.view.View.GONE);
	}

	//当Activity被销毁
	@Override
	protected void onDestroy() {
		LogUtil.i(TAG, "onDestroy");
		//能走到这里说明进程没被 GPU abort 干掉（abort 的话 onDestroy 不会执行），
		//所以撤掉"尝试中"标记，避免没跑过一帧就误判 GPU 不可用
		clearGpuPending();
		stopCamera();
		if (handLandmarker != null) {
			handLandmarker.close();
			handLandmarker = null;
		}
		if (analysisExecutor != null) {
			analysisExecutor.shutdown();
			analysisExecutor = null;
		}
		unbindService(mConnection);
		BLEManager.getInstance().unregister(this);
		super.onDestroy();
		handler.removeCallbacksAndMessages(null);
	}

	private void setupBackPressedHandler() {
		onBackPressedCallback = new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (BLEManager.getInstance().isConnected()) {
					handleBackWhenConnected();
				} else {
					handleBackWhenDisconnected();
				}
			}
		};

		getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
	}

	private void handleBackWhenConnected() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.exit_tips_title)
				.setMessage(R.string.exit_tips_content)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					exitApplication();
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setOnCancelListener(dialog -> {
					// 用户通过返回键取消对话框，不执行任何操作
				})
				.show();
	}

	private void handleBackWhenDisconnected() {
		if (!confirm) {
			// 第一次点击
			confirm = true;
			Toast.makeText(this, R.string.exit_remind, Toast.LENGTH_SHORT).show();

			handler.postDelayed(() -> confirm = false, 2000);
		} else {
			// 第二次点击
			exitApplication();
		}
	}

	private void exitApplication() {
		// 停止蓝牙服务
		Intent intent = new Intent(this, BLEService.class);
		stopService(intent);

		// 停止蓝牙管理
		BLEManager.getInstance().stop();
		BLEManager.getInstance().destroy();

		// 执行默认返回行为
		onBackPressedCallback.setEnabled(false);
		getOnBackPressedDispatcher().onBackPressed();

		// 完全退出应用
		finishAffinity();  // 先关闭所有 Activity
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	//mediapipe方法

	private void setupCameraClick() {
		ImageButton cameraButton = findViewById(R.id.button_camera);
		cameraButton.setOnClickListener(v -> {
			usingFrontCamera = !usingFrontCamera;
			startCamera();
		});
	}

	/**
	 * 绑定 CameraX 用例，并把分析帧送入 MediaPipe.
	 */
	private void startCamera() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
				!= PackageManager.PERMISSION_GRANTED) {
			PermissionHelperBluetooth.checkAndRequestPermissions(
					this, new String[]{Manifest.permission.CAMERA});
			return;
		}

		if (analysisExecutor == null || analysisExecutor.isShutdown()) {
			analysisExecutor = Executors.newSingleThreadExecutor();
		}

		final ListenableFuture<ProcessCameraProvider> providerFuture =
				ProcessCameraProvider.getInstance(this);
		providerFuture.addListener(() -> {
			try {
				cameraProvider = providerFuture.get();
			} catch (Exception e) {
				Log.e(TAG, "Camera provider unavailable", e);
				return;
			}
			if (isFinishing() || isDestroyed()) {
				return;
			}
			bindCameraUseCases();
		}, ContextCompat.getMainExecutor(this));
	}

	private void bindCameraUseCases() {
		CameraSelector cameraSelector =
				usingFrontCamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
		if (!hasCamera(cameraSelector)) {
			//设备没有该摄像头，退回另一个
			usingFrontCamera = !usingFrontCamera;
			cameraSelector = usingFrontCamera
					? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
			if (!hasCamera(cameraSelector)) {
				LogUtil.showToast(this, "未找到可用摄像头");
				return;
			}
		}

		ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
				.setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
				.setResolutionStrategy(new ResolutionStrategy(
						new Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
				.build();

		Preview preview = new Preview.Builder()
				.setResolutionSelector(resolutionSelector)
				.build();
		preview.setSurfaceProvider(previewView.getSurfaceProvider());

		ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
				.setResolutionSelector(resolutionSelector)
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
				.build();
		imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

		//前置摄像头预览会被 PreviewView 镜像，叠加层需同步镜像
		landmarkOverlay.setMirrored(usingFrontCamera);
		//切换摄像头后旧结果属于上一路画面，先清掉避免用错坐标系绘制
		landmarkOverlay.setResult(null);

		cameraProvider.unbindAll();
		cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
	}

	/**
	 * 设备是否有指定摄像头；无法获取摄像头信息时按“没有”处理并回退到另一个.
	 */
	private boolean hasCamera(CameraSelector cameraSelector) {
		if (cameraProvider == null) {
			return false;
		}
		try {
			return cameraProvider.hasCamera(cameraSelector);
		} catch (CameraInfoUnavailableException e) {
			Log.w(TAG, "Camera info unavailable", e);
			return false;
		}
	}

	private void stopCamera() {
		if (cameraProvider != null) {
			cameraProvider.unbindAll();
			cameraProvider = null;
		}
		landmarkOverlay.setResult(null);
	}

	/**
	 * 分析线程回调，不能在此阻塞等待推理结果.
	 */
	private void analyzeFrame(ImageProxy imageProxy) {
		try {
			if (handLandmarker == null) {
				return;
			}
			Bitmap bitmap = imageProxy.toBitmap();
			if (bitmap == null) {
				return;
			}
			int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

			//先把画面旋正再送推理，而不是把旋转角交给 MediaPipe。
			//
			//MediaPipe 返回的关键点坐标是在“送进去的那张图”的坐标系里的：它内部虽然会把
			//关键点投影回输入图，但输入图本身是未经旋转的传感器帧（横向），而屏幕上放的
			//PreviewView 显示的是旋正后的画面，两者差 90°，画出来就是横的。
			//自己旋正后输入图与预览画面坐标系一致，关键点可直接按同一套比例绘制。
			Bitmap upright = rotateBitmap(bitmap, rotationDegrees);
			landmarkOverlay.setSourceSize(upright.getWidth(), upright.getHeight());

			MPImage mpImage = new BitmapImageBuilder(upright).build();
			//时间戳必须严格递增
			long timestampMs = System.currentTimeMillis();
			if (timestampMs <= lastFrameTimestampMs) {
				timestampMs = lastFrameTimestampMs + 1;
			}
			lastFrameTimestampMs = timestampMs;
			try {
				//图已旋正，不再传旋转角
				handLandmarker.detectAsync(mpImage, timestampMs);
			} catch (RuntimeException e) {
				//提交失败时结果回调不会触发，需在此回收，否则位图无人释放
				mpImage.close();
				throw e;
			}
		} catch (RuntimeException e) {
			Log.e(TAG, "Hand landmark detection failed", e);
		} finally {
			imageProxy.close();
		}
	}

	/**
	 * 按给定角度旋正位图。
	 *
	 * <p>角度为 0 时原样返回；否则返回新位图并回收原图（原图不再被引用）。
	 */
	private static Bitmap rotateBitmap(Bitmap source, int degrees) {
		if (degrees == 0) {
			return source;
		}
		Matrix matrix = new Matrix();
		matrix.postRotate(degrees);
		Bitmap rotated = Bitmap.createBitmap(
				source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
		if (rotated != source) {
			source.recycle();
		}
		return rotated;
	}

	/**
	 * 获取手部坐标并进行相关处理
	 */
	private void processWristLandmark(HandLandmarkerResult result) {
		List<List<NormalizedLandmark>> normalizedHands = result.landmarks();
		List<List<Landmark>> worldHands = result.worldLandmarks();
		if (normalizedHands.isEmpty() || worldHands.isEmpty()) {
			return;
		}

		//获取世界坐标
		List<Landmark> worldLandmarks = worldHands.get(0);
		Landmark wrist = worldLandmarks.get(HandLandmark.WRIST);
		Landmark thumbTip = worldLandmarks.get(HandLandmark.THUMB_TIP);
		Landmark indexFingerTip = worldLandmarks.get(HandLandmark.INDEX_FINGER_TIP);
		Landmark middleFingerMCP = worldLandmarks.get(HandLandmark.MIDDLE_FINGER_MCP);

		//一号舵机
		short s1ServoAngle = LandmarkUtil.getS1ServoMove(indexFingerTip, thumbTip);
		ServoAction finger = new ServoAction((byte) 1, s1ServoAngle);

		//二号舵机
		short s2ServoAngle = LandmarkUtil.getS2ServoMove(wrist.x(), wrist.y());
		ServoAction wristLR = new ServoAction((byte) 2, s2ServoAngle);

		//三号舵机
		short s3ServoAngle = LandmarkUtil.getS3ServoMove(
				(indexFingerTip.z() + thumbTip.z()) / 2 - middleFingerMCP.z(),
				(indexFingerTip.y() + thumbTip.y()) / 2 - middleFingerMCP.y());
		ServoAction wristUD = new ServoAction((byte) 3, s3ServoAngle);

		//机械大小臂的逆运动学分解
		//获取归一化坐标
		List<NormalizedLandmark> normalizedLandmarks = normalizedHands.get(0);
		NormalizedLandmark normalizedWrist = normalizedLandmarks.get(HandLandmark.WRIST);
		NormalizedLandmark normalizedMiddleFingerMCP = normalizedLandmarks.get(HandLandmark.MIDDLE_FINGER_MCP);
		//计算小指根到掌根的归一化距离
		float normalizedDistance = LandmarkUtil.normalizedDistanceOfTwoPoint(normalizedWrist, normalizedMiddleFingerMCP);
		//由归一化坐标推出实际离摄像头距离
		float distance = (90 - 200 * normalizedDistance);
		//计算手掌几何中心的归一化坐标
		float centralX = ((normalizedWrist.x() + normalizedMiddleFingerMCP.x() - 1) * 40);
		float centralY = (float) ((1.5 - normalizedWrist.y() - normalizedMiddleFingerMCP.y()) * 40);
		short[] d = LandmarkUtil.getS456ServoMove(70 - distance, centralY, centralX);
/*		ServoAction servo4 = new ServoAction((byte) 4, d[1]);
		ServoAction servo5 = new ServoAction((byte) 5, d[0]);*/
		Log.i(
				TAG,
				String.format(
						"Media坐标:A= %s B= %s A= %f B= %f D=%f",
						d[0],
						d[1],
						centralX,
						centralY,
						70 - distance
				));

		if (bluetoothIsConnected) {
			CmdUtil.sendCMDToBluetooth(CmdUtil.CMD_MULT_SERVO_MOVE((short) 500, finger, wristLR, wristUD));
		}
	}

	//蓝牙

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			LogUtil.i(TAG, "BLE Service connected");
			BLEService bleService = ((BLEService.BLEBinder) service).getService();
			BLEManager.getInstance().init(bleService);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			LogUtil.w(TAG, "BLE Service disconnected");
			BLEManager.getInstance().destroy();
		}
	};

	@Override
	public void onDeviceSelected(BluetoothDevice device) {
		try {
			LogUtil.i(TAG, "bond state = " + device.getBondState());
		} catch (SecurityException e) {
			LogUtil.e(TAG, e.toString());
		}
		mBluetoothDevice = device;
		bleManager.connect(device);
//        setState(R.string.bluetooth_state_connecting);
		Toast.makeText(this, R.string.bluetooth_state_connecting, Toast.LENGTH_SHORT).show();
	}

	//蓝牙按钮的点击事件
	public void setupBluetoothClick() {
		ImageButton loadBluetoothButton = findViewById(R.id.bluetooth_btn);
		loadBluetoothButton.setOnClickListener(v -> {
			if (mBluetoothAdapter == null) {
				Toast.makeText(getBaseContext(), R.string.tips_bluetooth_not_support, Toast.LENGTH_SHORT).show();
				return;
			}
			PermissionHelperBluetooth.checkAndRequestBluetoothPermissions(this);
			if (!isBluetoothEnabled()) {
				Toast.makeText(getBaseContext(), R.string.tips_open_bluetooth, Toast.LENGTH_SHORT).show();
				startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
				return;
			}
			if (bluetoothIsConnected) {
				PromptDialog.create(getBaseContext(), getSupportFragmentManager(), getString(R.string.disconnect_tips_title),
						getString(R.string.disconnect_bluetooth_tips_connect), (dialog, which) -> {
							if (DialogInterface.BUTTON_POSITIVE == which) {
								bleManager.stop();
							}
						});
			} else {
				SearchDialog.createDialog(getSupportFragmentManager(), this);
			}
		});
	}

	/**
	 * 读取蓝牙开关状态。
	 *
	 * <p>Android 12 起读取该状态需要 BLUETOOTH_CONNECT，权限未授予时
	 * isEnabled() 会抛 SecurityException；权限申请是异步的，首次点击时往往还没授予，
	 * 因此这里按“未开启”处理，由调用方提示用户。
	 */
	private boolean isBluetoothEnabled() {
		try {
			return mBluetoothAdapter.isEnabled();
		} catch (SecurityException e) {
			LogUtil.w(TAG, "缺少蓝牙权限，无法读取开关状态: " + e);
			return false;
		}
	}

	//设置按钮的点击事件
	public void setupSetClick() {
		ImageButton loadSetButton = findViewById(R.id.set_btn);
		loadSetButton.setOnClickListener(v -> {
			exitApplication();
		});
	}

	//复位按钮的点击事件
	public void setupTestClick() {
		ImageButton loadSetButton = findViewById(R.id.test_btn);
		loadSetButton.setOnClickListener(v -> {
			ServoAction move1new = new ServoAction((byte) 1, (short) 1500);
			ServoAction move2new = new ServoAction((byte) 2, (short) 1500);
			ServoAction move3new = new ServoAction((byte) 3, (short) 1500);
			ServoAction move4new = new ServoAction((byte) 4, (short) 1500);
			ServoAction move5new = new ServoAction((byte) 5, (short) 1500);
			CmdUtil.sendCMDToBluetooth(CmdUtil.CMD_MULT_SERVO_MOVE((short) 2000, move1new, move2new, move3new, move4new, move5new));
		});
	}

	//网络按钮的点击事件
	public void setupNetworkClick() {
		ImageButton loadBluetoothButton = findViewById(R.id.network_btn);
		loadBluetoothButton.setOnClickListener(v -> {
			//检查网络连接是否可用
			if (!isNetworkAvailable()) {
				LogUtil.showToast(this, "请检查网络是否已连接");
			}
			if (networkIsConnected) {
				PromptDialog.create(getBaseContext(), getSupportFragmentManager(), getString(R.string.disconnect_tips_title),
						getString(R.string.disconnect_network_tips_connect), (dialog, which) -> {
							if (DialogInterface.BUTTON_POSITIVE == which) {
								//TODO 断开连接
							}
						});
			} else {
				NetworkDialog.createDialog(getSupportFragmentManager(), this);
			}
		});
	}

	/**
	 * 当前是否存在已连接且可用的网络.
	 * getActiveNetworkInfo() 在离线时返回 null，且自 API 29 起已废弃.
	 */
	private boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager =
				(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager == null) {
			return false;
		}
		Network network = connectivityManager.getActiveNetwork();
		if (network == null) {
			return false;
		}
		NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
		return capabilities != null
				&& capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
	}

	/**
	 * 设置蓝牙状态图片
	 */
	private void setState(boolean isConnected) {
		LogUtil.i(TAG, "isConnected = " + isConnected);
		if (isConnected) {
			MainActivity.bluetoothIsConnected = true;
			btStateBtn.setContentDescription(getString(R.string.bluetooth_state_connected));
			btStateBtn.setImageResource(R.drawable.ic_bluetooth_transient_animation_drawable_blue);
		} else {
			MainActivity.bluetoothIsConnected = false;
			btStateBtn.setContentDescription(getString(R.string.bluetooth_state_disconnected));
			btStateBtn.setImageResource(R.drawable.ic_bluetooth_transient_animation_red);
			Drawable drawable = btStateBtn.getDrawable();
			//循环播放断开状态的闪烁动画；不是动画矢量图时只做静态展示
			if (drawable instanceof AnimatedVectorDrawable) {
				AnimatedVectorDrawable anim = (AnimatedVectorDrawable) drawable;
				anim.registerAnimationCallback(new Animatable2.AnimationCallback() {
					@Override
					public void onAnimationEnd(Drawable who) {
						super.onAnimationEnd(who);
						anim.start();
					}
				});
				anim.start();
			} else if (drawable instanceof Animatable) {
				((Animatable) drawable).start();
			}
		}
	}

	class MsgCallBack implements Handler.Callback {//处理消息

		@Override
		public boolean handleMessage(Message msg) {
			switch (msg.what) {
				case Constants.MessageID.MSG_CONNECT_SUCCEED:
					LogUtil.i(TAG, "connected ");
//                    setState(R.string.bluetooth_state_connected);
					Toast.makeText(getBaseContext(), R.string.bluetooth_state_connected, Toast.LENGTH_SHORT).show();
					setState(true);
					break;
				case Constants.MessageID.MSG_CONNECT_FAILURE:
					if (connectTimes < RETRY_TIMES) {
						connectTimes++;
						mHandler.sendEmptyMessageDelayed(Constants.MessageID.MSG_CONNECT_RECONNECT, 300);
					} else {
						connectTimes = 0;
//                        setState(R.string.bluetooth_state_connect_failure);
						Toast.makeText(getBaseContext(), R.string.bluetooth_state_connect_failure, Toast.LENGTH_SHORT).show();
						setState(false);
					}
					break;
				case Constants.MessageID.MSG_CONNECT_RECONNECT:
					try {
						LogUtil.i(TAG, "reconnect bluetooth" + mBluetoothDevice.getName() + " " + connectTimes);
					} catch (SecurityException e) {
						LogUtil.e(TAG, e.toString());
					}
					bleManager.connect(mBluetoothDevice);
					break;
				case Constants.MessageID.MSG_CONNECT_LOST:
//                    setState(R.string.bluetooth_state_disconnected);
					Toast.makeText(getBaseContext(), R.string.disconnect_bluetooth_tips_succeed, Toast.LENGTH_SHORT).show();
					setState(false);
					break;
				case Constants.MessageID.MSG_SEND_COMMAND:
					bleManager.send((ByteCommand) msg.obj);
					Message sendMsg = mHandler.obtainMessage(Constants.MessageID.MSG_SEND_COMMAND, msg.arg1, -1, msg.obj);
					mHandler.sendMessageDelayed(sendMsg, msg.arg1);
					break;

				case Constants.MessageID.MSG_SEND_NOT_CONNECT:
					Toast.makeText(getBaseContext(), R.string.send_tips_bluetooth_no_connected, Toast.LENGTH_SHORT).show();
					break;
			}
			return true;
		}
	}
}
