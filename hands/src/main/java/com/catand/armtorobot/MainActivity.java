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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

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
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 应用程序的主Activity.
 */
public class MainActivity extends AppCompatActivity implements SearchDialog.OnDeviceSelectedListener, NetworkDialog.OnDeviceSelectedListener {
	private FirebaseAnalytics mFirebaseAnalytics;
	private static final String TAG = MainActivity.class.getSimpleName();

	private Hands hands;
	private final int HANDS_NUM = 1;
	//在 GPU 或 CPU 上运行管道和模型推理.
	private static final boolean RUN_ON_GPU = true;

	private enum InputSource {
		UNKNOWN,
		IMAGE,
		CAMERA,
	}

	private InputSource inputSource = InputSource.UNKNOWN;

	// 图像演示 UI 和图像加载器组件.
	private ActivityResultLauncher<Intent> imageGetter;
	private HandsResultImageView imageView;
	// 实时摄像头演示 UI 和摄像头组件.
	private CameraInput cameraInput;

	private SolutionGlSurfaceView<HandsResult> glSurfaceView;

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

	//通用方法

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// 初始化 Firebase
		mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

		//初始化按钮点击事件监听器
		setupStaticImageDemoUiComponents();
		setupLiveDemoUiComponents();
		setupBluetoothClick();
		setupSetClick();
		setupTestClick();
		setupNetworkClick();

		if (!BluetoothUtils.isSupport(BluetoothAdapter.getDefaultAdapter())) {
			Toast.makeText(this, "蓝牙不可用", Toast.LENGTH_LONG).show();
			finish();
		}

		//创建蓝牙按钮对象
		btStateBtn = findViewById(R.id.bluetooth_btn);
		Intent intent = new Intent(this, BLEService.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		BLEManager.getInstance().register(this);
		mHandler = new Handler(new MsgCallBack());
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		//重置连接尝试次数
		connectTimes = 0;
	}

	//当应用恢复前台运行
	@Override
	protected void onResume() {
		super.onResume();

		//相机
		//如果目前工作源是摄像头,就重启相机和opengl表面渲染
		if (inputSource == InputSource.CAMERA) {
			cameraInput = new CameraInput(this);
			cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
			glSurfaceView.post(this::startCamera);
			glSurfaceView.setVisibility(View.VISIBLE);
		}

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
		//如果目前工作源是摄像头,就完全隐藏输出界面并关闭视频流
		if (inputSource == InputSource.CAMERA) {
			glSurfaceView.setVisibility(View.GONE);
			cameraInput.close();
		}
	}

	//当Activity被销毁
	@Override
	protected void onDestroy() {
		LogUtil.i(TAG, "onCreate");
		unbindService(mConnection);
		BLEManager.getInstance().unregister(this);
		super.onDestroy();
	}

	//当返回键被按下
	@Override
	public void onBackPressed() {
		if (BLEManager.getInstance().isConnected()) {
			PromptDialog.create(this, getFragmentManager(), getString(R.string.exit_tips_title),
					getString(R.string.exit_tips_content), (dialog, which) -> {
						if (DialogInterface.BUTTON_POSITIVE == which) {
							BLEManager.getInstance().stop();
							MainActivity.super.onBackPressed();
							android.os.Process.killProcess(android.os.Process.myPid());
						}
					});
		} else {
			if (!confirm) {
				confirm = true;
				Toast.makeText(this, R.string.exit_remind, Toast.LENGTH_SHORT).show();
				Timer timer = new Timer();
				timer.schedule(new TimerTask() {

					@Override
					public void run() {
						confirm = false;
					}
				}, 2000);
			} else {
				Intent intent = new Intent(this, BLEService.class);
				stopService(intent);
				BLEManager.getInstance().destroy();
				super.onBackPressed();
				android.os.Process.killProcess(android.os.Process.myPid());
			}
		}
	}

	//mediapipe方法

	//把原Bitmap等比例无裁剪缩小到适合imageView的大小
	private Bitmap downscaleBitmap(Bitmap originalBitmap) {
		//纵横比
		double aspectRatio = (double) originalBitmap.getWidth() / originalBitmap.getHeight();
		int width = imageView.getWidth();
		int height = imageView.getHeight();
		if (((double) imageView.getWidth() / imageView.getHeight()) > aspectRatio) {
			width = (int) (height * aspectRatio);
		} else {
			height = (int) (width / aspectRatio);
		}
		return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
	}

	private Bitmap rotateBitmap(Bitmap inputBitmap, InputStream imageData) throws IOException {
		int orientation =
				new ExifInterface(imageData)
						.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		if (orientation == ExifInterface.ORIENTATION_NORMAL) {
			return inputBitmap;
		}
		Matrix matrix = new Matrix();
		switch (orientation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				matrix.postRotate(90);
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				matrix.postRotate(180);
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				matrix.postRotate(270);
				break;
			default:
				matrix.postRotate(0);
		}
		return Bitmap.createBitmap(
				inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);
	}

	/**
	 * 为静态图像演示设置 UI 组件.
	 */
	private void setupStaticImageDemoUiComponents() {
		//访问图库并将图像作为位图读取的意图.
		imageGetter =
				registerForActivityResult(
						new ActivityResultContracts.StartActivityForResult(),
						result -> {
							Intent resultIntent = result.getData();
							if (resultIntent != null) {
								if (result.getResultCode() == RESULT_OK) {
									Bitmap bitmap = null;
									try {
										bitmap =
												downscaleBitmap(
														MediaStore.Images.Media.getBitmap(
																this.getContentResolver(), resultIntent.getData()));
									} catch (IOException e) {
										Log.e(TAG, "Bitmap reading error:" + e);
									}
									try {
										InputStream imageData =
												this.getContentResolver().openInputStream(resultIntent.getData());
										bitmap = rotateBitmap(bitmap, imageData);
									} catch (IOException e) {
										Log.e(TAG, "Bitmap rotation error:" + e);
									}
									if (bitmap != null) {
										hands.send(bitmap);
									}
								}
							}
						});
		Button loadImageButton = findViewById(R.id.button_load_picture);
		loadImageButton.setOnClickListener(
				v -> {
					if (inputSource != InputSource.IMAGE) {
						stopCurrentPipeline();
						setupStaticImageModePipeline();
					}
					//从图库中读取图像
					Intent pickImageIntent = new Intent(Intent.ACTION_PICK);
					pickImageIntent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");
					imageGetter.launch(pickImageIntent);
				});
		imageView = new HandsResultImageView(this);
	}

	/**
	 * 为静态图像模式设置核心工作流.
	 */
	private void setupStaticImageModePipeline() {
		this.inputSource = InputSource.IMAGE;
		//在静态图像模式下初始化一个新的 MediaPipe Hands 解决方案实例.
		hands =
				new Hands(
						this,
						HandsOptions.builder()
								.setStaticImageMode(true)
								.setMaxNumHands(HANDS_NUM)
								.setRunOnGpu(RUN_ON_GPU)
								.build());

		//将 MediaPipe Hands 解决方案连接到用户定义的 HandsResultImageView.
		hands.setResultListener(
				handsResult -> {
					processWristLandmark(handsResult, true);
					imageView.setHandsResult(handsResult);
					runOnUiThread(() -> imageView.update());
				});
		hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

		// 更新预览布局.
		FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
		frameLayout.removeAllViewsInLayout();
		imageView.setImageDrawable(null);
		frameLayout.addView(imageView);
		imageView.setVisibility(View.VISIBLE);
	}

	/**
	 * 使用摄像头输入为动态演示设置 UI 组件.
	 */
	private void setupLiveDemoUiComponents() {
		Button startCameraButton = findViewById(R.id.button_start_camera);
		startCameraButton.setOnClickListener(
				v -> {
					if (inputSource == InputSource.CAMERA) {
						return;
					}
					stopCurrentPipeline();
					setupStreamingModePipeline();
				});
	}

	/**
	 * 为实时模式设置核心工作流程.
	 */
	private void setupStreamingModePipeline() {
		this.inputSource = InputSource.CAMERA;

		//在摄像头模式下初始化一个新的 MediaPipe Hands 解决方案实例
		hands =
				new Hands(
						this,
						HandsOptions.builder()
								.setStaticImageMode(false)
								.setMaxNumHands(HANDS_NUM)
								.setRunOnGpu(RUN_ON_GPU)
								.build());
		hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

		cameraInput = new CameraInput(this);
		cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));

		//使用用户定义的 HandsResultGlRenderer 初始化新的 GLSurfaceView
		glSurfaceView =
				new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
		glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
		glSurfaceView.setRenderInputImage(true);
		hands.setResultListener(
				handsResult -> {
					processWristLandmark(handsResult, /*showPixelValues=*/ false);
					glSurfaceView.setRenderData(handsResult);
					glSurfaceView.requestRender();
				});

		// 附加 GLSurfaceView 后可启动相机
		// 对于视频输入源，videoInput.start() 将在视频 uri 可用时调用.
		glSurfaceView.post(this::startCamera);

		//更新预览布局
		FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
		imageView.setVisibility(View.GONE);
		frameLayout.removeAllViewsInLayout();
		frameLayout.addView(glSurfaceView);
		glSurfaceView.setVisibility(View.VISIBLE);
		frameLayout.requestLayout();
	}

	private void startCamera() {
		cameraInput.start(
				this,
				hands.getGlContext(),
				CameraInput.CameraFacing.FRONT,
				glSurfaceView.getWidth(),
				glSurfaceView.getHeight());
	}

	/**
	 * 停止当前的工作流
	 */
	private void stopCurrentPipeline() {
		if (cameraInput != null) {
			cameraInput.setNewFrameListener(null);
			cameraInput.close();
		}
		if (glSurfaceView != null) {
			glSurfaceView.setVisibility(View.GONE);
		}
		if (hands != null) {
			hands.close();
		}
	}

	/**
	 * 获取手部坐标并进行相关处理
	 */
	private void processWristLandmark(HandsResult result, boolean showPixelValues) {
		if (result.multiHandLandmarks().isEmpty()) {
			return;
		}

		//获取世界坐标
		LandmarkProto.Landmark wrist = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
		LandmarkProto.Landmark thumbTip = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.THUMB_TIP);
		LandmarkProto.Landmark indexFingerTip = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.INDEX_FINGER_TIP);
		LandmarkProto.Landmark middleFingerMCP = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.MIDDLE_FINGER_MCP);

		//一号舵机
		short s1ServoAngle = LandmarkUtil.getS1ServoMove(indexFingerTip, thumbTip);
		ServoAction finger = new ServoAction((byte) 1, s1ServoAngle);

		//二号舵机
		short s2ServoAngle = LandmarkUtil.getS2ServoMove(wrist.getX(), wrist.getY());
		ServoAction wristLR = new ServoAction((byte) 2, s2ServoAngle);

		//三号舵机
		short s3ServoAngle = LandmarkUtil.getS3ServoMove(
				(indexFingerTip.getZ() + thumbTip.getZ()) / 2 - middleFingerMCP.getZ(),
				(indexFingerTip.getY() + thumbTip.getY()) / 2 - middleFingerMCP.getY());
		ServoAction wristUD = new ServoAction((byte) 3, s3ServoAngle);

		//机械大小臂的逆运动学分解
		//获取归一化坐标
		LandmarkProto.NormalizedLandmark normalizedWrist = result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
		LandmarkProto.NormalizedLandmark normalizedMiddleFingerMCP = result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.MIDDLE_FINGER_MCP);
		//计算小指根到掌根的归一化距离
		float normalizedDistance = LandmarkUtil.normalizedDistanceOfTwoPoint(normalizedWrist, normalizedMiddleFingerMCP);
		//由归一化坐标推出实际离摄像头距离
		float distance = (90 - 200 * normalizedDistance);
		//计算手掌几何中心的归一化坐标
		float centralX = ((normalizedWrist.getX() + normalizedMiddleFingerMCP.getX() - 1) * 40);
		float centralY = (float) ((1.5 - normalizedWrist.getY() - normalizedMiddleFingerMCP.getY()) * 40);
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

	public int getHandCameraDistance() {
		//TODO
		return 0;
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
			PermissionHelperBluetooth.checkAndRequestBluetoothPermissions(this);
			if (mBluetoothAdapter.isEnabled()) {
				if (bluetoothIsConnected) {
					PromptDialog.create(getBaseContext(), getFragmentManager(), getString(R.string.disconnect_tips_title),
							getString(R.string.disconnect_bluetooth_tips_connect), (dialog, which) -> {
								if (DialogInterface.BUTTON_POSITIVE == which) {
									bleManager.stop();
								}
							});
				} else {
					SearchDialog.createDialog(getFragmentManager(), this);
				}
			} else {
				Toast.makeText(getBaseContext(), R.string.tips_open_bluetooth, Toast.LENGTH_SHORT).show();
				startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
			}
		});
	}

	//设置按钮的点击事件
	public void setupSetClick() {
		ImageButton loadSetButton = findViewById(R.id.set_btn);
		loadSetButton.setOnClickListener(v -> {
			BLEManager.getInstance().stop();
			MainActivity.super.onBackPressed();
			android.os.Process.killProcess(android.os.Process.myPid());
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
			if (!((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo().isAvailable()) {
				LogUtil.showToast(this, "请检查网络是否已连接");
			}
			if (networkIsConnected) {
				PromptDialog.create(getBaseContext(), getFragmentManager(), getString(R.string.disconnect_tips_title),
						getString(R.string.disconnect_network_tips_connect), (dialog, which) -> {
							if (DialogInterface.BUTTON_POSITIVE == which) {
								//TODO 断开连接
							}
						});
			} else {
				NetworkDialog.createDialog(getFragmentManager(), this);
			}
		});
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
			AnimatedVectorDrawable anim = (AnimatedVectorDrawable) btStateBtn.getDrawable();
			anim.start();
			anim.registerAnimationCallback(new Animatable2.AnimationCallback() {
				@Override
				public void onAnimationEnd(Drawable drawable) {
					super.onAnimationEnd(drawable);
					anim.start();
				}
			});
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
