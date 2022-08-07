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
import com.catand.armtorobot.widget.PromptDialog;
import com.catand.armtorobot.widget.SearchDialog;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;
import com.rohitss.uceh.UCEHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 应用程序的主Activity.
 */
public class MainActivity extends AppCompatActivity implements SearchDialog.OnDeviceSelectedListener {
	private static final String TAG = MainActivity.class.getSimpleName();

	private Hands hands;
	private final int HANDS_NUM = 1;
	//在 GPU 或 CPU 上运行管道和模型推理.
	private static final boolean RUN_ON_GPU = true;

	private enum InputSource {
		UNKNOWN,
		IMAGE,
		VIDEO,
		CAMERA,
	}

	private InputSource inputSource = InputSource.UNKNOWN;

	// 图像演示 UI 和图像加载器组件.
	private ActivityResultLauncher<Intent> imageGetter;
	private HandsResultImageView imageView;
	// 视频演示 UI 和视频加载器组件.
	private VideoInput videoInput;
	private ActivityResultLauncher<Intent> videoGetter;
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
	public static boolean isConnected;

	//通用方法

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// 初始化 UCE_Handler 库
		new UCEHandler.Builder(this).build();

		//初始化按钮点击事件监听器
		setupStaticImageDemoUiComponents();
		setupVideoDemoUiComponents();
		setupLiveDemoUiComponents();
		setupBluetoothClick();
		setupSetClick();
		setupTestClick();

		if (!BluetoothUtils.isSupport(BluetoothAdapter.getDefaultAdapter())) {
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
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
		} else {
			//如果目前工作源是视频,就继续播放
			if (inputSource == InputSource.VIDEO) {
				videoInput.resume();
			}
		}

		//蓝牙
		bleManager = BLEManager.getInstance();
		isConnected = bleManager.isConnected();
		bleManager.setHandler(mHandler);
		LogUtil.i(TAG, "onResume isConnected= " + isConnected);
		if (isConnected) {
			btStateBtn.setContentDescription(getString(R.string.bluetooth_state_connected));
			btStateBtn.setImageResource(R.drawable.bluetooth_connected);
		} else {
			btStateBtn.setContentDescription(getString(R.string.bluetooth_state_disconnected));
			btStateBtn.setImageResource(R.drawable.bluetooth_disconnected);
		}
	}

	//当应用进入后台
	@Override
	protected void onPause() {
		super.onPause();
		//如果目前工作源是摄像头,就完全隐藏输出界面并关闭视频流
		if (inputSource == InputSource.CAMERA) {
			glSurfaceView.setVisibility(View.GONE);
			cameraInput.close();
		} else
			//如果目前工作源是视频,就暂停播放
			if (inputSource == InputSource.VIDEO) {
				videoInput.pause();
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
					logWristLandmark(handsResult, true);
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
	 * 为视频演示设置 UI 组件.
	 */
	private void setupVideoDemoUiComponents() {
		// 访问图库和读取视频文件的意图.
		videoGetter =
				registerForActivityResult(
						new ActivityResultContracts.StartActivityForResult(),
						result -> {
							Intent resultIntent = result.getData();
							if (resultIntent != null) {
								if (result.getResultCode() == RESULT_OK) {
									glSurfaceView.post(
											() ->
													videoInput.start(
															this,
															resultIntent.getData(),
															hands.getGlContext(),
															glSurfaceView.getWidth(),
															glSurfaceView.getHeight()));
								}
							}
						});
		Button loadVideoButton = findViewById(R.id.button_load_video);
		loadVideoButton.setOnClickListener(
				v -> {
					stopCurrentPipeline();
					//把视频文件作为视频流输入摄像头模式
					setupStreamingModePipeline(InputSource.VIDEO);
					//从图库中读取视频
					Intent pickVideoIntent = new Intent(Intent.ACTION_PICK);
					pickVideoIntent.setDataAndType(MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video/*");
					videoGetter.launch(pickVideoIntent);
				});
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
					setupStreamingModePipeline(InputSource.CAMERA);
				});
	}

	/**
	 * 为实时模式设置核心工作流程.
	 */
	private void setupStreamingModePipeline(InputSource inputSource) {
		this.inputSource = inputSource;

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

		if (inputSource == InputSource.CAMERA) {
			cameraInput = new CameraInput(this);
			cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
		} else if (inputSource == InputSource.VIDEO) {
			videoInput = new VideoInput(this);
			videoInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
		}

		//使用用户定义的 HandsResultGlRenderer 初始化新的 GLSurfaceView
		glSurfaceView =
				new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
		glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
		glSurfaceView.setRenderInputImage(true);
		hands.setResultListener(
				handsResult -> {
					logWristLandmark(handsResult, /*showPixelValues=*/ false);
					glSurfaceView.setRenderData(handsResult);
					glSurfaceView.requestRender();
				});

		// 附加 GLSurfaceView 后可启动相机
		// 对于视频输入源，videoInput.start() 将在视频 uri 可用时调用.
		if (inputSource == InputSource.CAMERA) {
			glSurfaceView.post(this::startCamera);
		}

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
		if (videoInput != null) {
			videoInput.setNewFrameListener(null);
			videoInput.close();
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
	private void logWristLandmark(HandsResult result, boolean showPixelValues) {
		if (result.multiHandLandmarks().isEmpty()) {
			return;
		}

		LandmarkProto.Landmark indexFingerTip = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.INDEX_FINGER_TIP);
		LandmarkProto.Landmark thumbTip = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.THUMB_TIP);
		LandmarkProto.Landmark wrist = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
		LandmarkProto.Landmark middleFingerMCP = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.MIDDLE_FINGER_MCP);

		//一号舵机
		short s1ServoAngle = LandmarkUtil.distanceToS1ServoMove(indexFingerTip, thumbTip);
		ServoAction finger = new ServoAction((byte) 1, s1ServoAngle);

		//二号舵机
		short s2ServoAngle = LandmarkUtil.coordinateToS2ServoMove(wrist.getX(), wrist.getY());
		ServoAction wristLR = new ServoAction((byte) 2, s2ServoAngle);

		//三号舵机
		short s3ServoAngle = LandmarkUtil.coordinateToS3ServoMove(
				(indexFingerTip.getZ() + thumbTip.getZ())/2-middleFingerMCP.getZ(),
				(indexFingerTip.getY() + thumbTip.getY())/2-middleFingerMCP.getY());
		ServoAction wristUD = new ServoAction((byte) 3, s3ServoAngle);

		CmdUtil.CMD_MULT_SERVO_MOVE((short) 200, finger, wristLR, wristUD);

		Log.i(
				TAG,
				String.format("MediaYZ: %s", s3ServoAngle));

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
				if (isConnected) {
					PromptDialog.create(getBaseContext(), getFragmentManager(), getString(R.string.disconnect_tips_title),
							getString(R.string.disconnect_tips_connect), (dialog, which) -> {
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

	//设置测试按钮的点击事件
	public void setupTestClick() {
		Button loadSetButton = findViewById(R.id.test_btn);
		loadSetButton.setOnClickListener(v -> {
			ServoAction move1new = new ServoAction((byte) 1, (short) 500);
			ServoAction move2new = new ServoAction((byte) 2, (short) 2000);
			ServoAction move3new = new ServoAction((byte) 3, (short) 2000);
			ServoAction move4new = new ServoAction((byte) 4, (short) 1000);
			ServoAction move5new = new ServoAction((byte) 5, (short) 2000);
			CmdUtil.CMD_MULT_SERVO_MOVE((short) 2000, move1new, move2new, move3new, move4new, move5new);
		});
	}

	private void setState(boolean isConnected) {//设置蓝牙状态图片
		LogUtil.i(TAG, "isConnected = " + isConnected);
		if (isConnected) {
			MainActivity.isConnected = true;
			btStateBtn.setImageResource(R.drawable.bluetooth_connected);
		} else {
			MainActivity.isConnected = false;
			btStateBtn.setImageResource(R.drawable.bluetooth_disconnected);
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
					Toast.makeText(getBaseContext(), R.string.disconnect_tips_succeed, Toast.LENGTH_SHORT).show();
					setState(false);
					break;
				case Constants.MessageID.MSG_SEND_COMMAND:
					bleManager.send((ByteCommand) msg.obj);
					Message sendMsg = mHandler.obtainMessage(Constants.MessageID.MSG_SEND_COMMAND, msg.arg1, -1, msg.obj);
					mHandler.sendMessageDelayed(sendMsg, msg.arg1);
					break;

				case Constants.MessageID.MSG_SEND_NOT_CONNECT:
					Toast.makeText(getBaseContext(), R.string.send_tips_no_connected, Toast.LENGTH_SHORT).show();
					break;
			}
			return true;
		}
	}
}
