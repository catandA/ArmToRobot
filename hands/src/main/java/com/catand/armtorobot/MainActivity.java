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
 * ??????????????????Activity.
 */
public class MainActivity extends AppCompatActivity implements SearchDialog.OnDeviceSelectedListener, NetworkDialog.OnRobotSelectedListener {
	private FirebaseAnalytics mFirebaseAnalytics;
	private static final String TAG = MainActivity.class.getSimpleName();

	private Hands hands;
	private final int HANDS_NUM = 1;
	//??? GPU ??? CPU ??????????????????????????????.
	private static final boolean RUN_ON_GPU = true;

	private enum InputSource {
		UNKNOWN,
		IMAGE,
		CAMERA,
	}

	private InputSource inputSource = InputSource.UNKNOWN;

	// ???????????? UI ????????????????????????.
	private ActivityResultLauncher<Intent> imageGetter;
	private HandsResultImageView imageView;
	// ????????????????????? UI ??????????????????.
	private CameraInput cameraInput;

	private SolutionGlSurfaceView<HandsResult> glSurfaceView;

	//??????????????????
	private static final int RETRY_TIMES = 3;
	//????????????
	private boolean confirm;
	//??????????????????
	private ImageButton btStateBtn;
	//???????????????
	public static BluetoothAdapter mBluetoothAdapter = null;
	public static BLEManager bleManager;
	public static BluetoothDevice mBluetoothDevice;
	private Handler mHandler;
	/**
	 * ?????????????????????
	 */
	private int connectTimes;
	/**
	 * ??????????????????
	 */
	public static boolean bluetoothIsConnected;
	/**
	 * ?????????????????????
	 */
	public static boolean networkIsConnected;

	//????????????

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// ????????? Firebase
		mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

		//????????????????????????????????????
		setupStaticImageDemoUiComponents();
		setupLiveDemoUiComponents();
		setupBluetoothClick();
		setupSetClick();
		setupTestClick();
		setupNetworkClick();

		if (!BluetoothUtils.isSupport(BluetoothAdapter.getDefaultAdapter())) {
			Toast.makeText(this, "???????????????", Toast.LENGTH_LONG).show();
			finish();
		}

		//????????????????????????
		btStateBtn = findViewById(R.id.bluetooth_btn);
		Intent intent = new Intent(this, BLEService.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		BLEManager.getInstance().register(this);
		mHandler = new Handler(new MsgCallBack());
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		//????????????????????????
		connectTimes = 0;
	}

	//???????????????????????????
	@Override
	protected void onResume() {
		super.onResume();

		//??????
		//?????????????????????????????????,??????????????????opengl????????????
		if (inputSource == InputSource.CAMERA) {
			cameraInput = new CameraInput(this);
			cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
			glSurfaceView.post(this::startCamera);
			glSurfaceView.setVisibility(View.VISIBLE);
		}

		//??????
		bleManager = BLEManager.getInstance();
		bluetoothIsConnected = bleManager.isConnected();
		bleManager.setHandler(mHandler);
		LogUtil.i(TAG, "onResume isConnected= " + bluetoothIsConnected);
		setState(bluetoothIsConnected);
	}

	//?????????????????????
	@Override
	protected void onPause() {
		super.onPause();
		//?????????????????????????????????,?????????????????????????????????????????????
		if (inputSource == InputSource.CAMERA) {
			glSurfaceView.setVisibility(View.GONE);
			cameraInput.close();
		}
	}

	//???Activity?????????
	@Override
	protected void onDestroy() {
		LogUtil.i(TAG, "onCreate");
		unbindService(mConnection);
		BLEManager.getInstance().unregister(this);
		super.onDestroy();
	}

	//?????????????????????
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

	//mediapipe??????

	//??????Bitmap?????????????????????????????????imageView?????????
	private Bitmap downscaleBitmap(Bitmap originalBitmap) {
		//?????????
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
	 * ??????????????????????????? UI ??????.
	 */
	private void setupStaticImageDemoUiComponents() {
		//???????????????????????????????????????????????????.
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
					//????????????????????????
					Intent pickImageIntent = new Intent(Intent.ACTION_PICK);
					pickImageIntent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");
					imageGetter.launch(pickImageIntent);
				});
		imageView = new HandsResultImageView(this);
	}

	/**
	 * ??????????????????????????????????????????.
	 */
	private void setupStaticImageModePipeline() {
		this.inputSource = InputSource.IMAGE;
		//????????????????????????????????????????????? MediaPipe Hands ??????????????????.
		hands =
				new Hands(
						this,
						HandsOptions.builder()
								.setStaticImageMode(true)
								.setMaxNumHands(HANDS_NUM)
								.setRunOnGpu(RUN_ON_GPU)
								.build());

		//??? MediaPipe Hands ???????????????????????????????????? HandsResultImageView.
		hands.setResultListener(
				handsResult -> {
					processWristLandmark(handsResult, true);
					imageView.setHandsResult(handsResult);
					runOnUiThread(() -> imageView.update());
				});
		hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

		// ??????????????????.
		FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
		frameLayout.removeAllViewsInLayout();
		imageView.setImageDrawable(null);
		frameLayout.addView(imageView);
		imageView.setVisibility(View.VISIBLE);
	}

	/**
	 * ?????????????????????????????????????????? UI ??????.
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
	 * ???????????????????????????????????????.
	 */
	private void setupStreamingModePipeline() {
		this.inputSource = InputSource.CAMERA;

		//?????????????????????????????????????????? MediaPipe Hands ??????????????????
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

		//????????????????????? HandsResultGlRenderer ??????????????? GLSurfaceView
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

		// ?????? GLSurfaceView ??????????????????
		// ????????????????????????videoInput.start() ???????????? uri ???????????????.
		glSurfaceView.post(this::startCamera);

		//??????????????????
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
	 * ????????????????????????
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
	 * ???????????????????????????????????????
	 */
	private void processWristLandmark(HandsResult result, boolean showPixelValues) {
		if (result.multiHandLandmarks().isEmpty()) {
			return;
		}

		//??????????????????
		LandmarkProto.Landmark wrist = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
		LandmarkProto.Landmark thumbTip = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.THUMB_TIP);
		LandmarkProto.Landmark indexFingerTip = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.INDEX_FINGER_TIP);
		LandmarkProto.Landmark middleFingerMCP = result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.MIDDLE_FINGER_MCP);

		//????????????
		short s1ServoAngle = LandmarkUtil.getS1ServoMove(indexFingerTip, thumbTip);
		ServoAction finger = new ServoAction((byte) 1, s1ServoAngle);

		//????????????
		short s2ServoAngle = LandmarkUtil.getS2ServoMove(wrist.getX(), wrist.getY());
		ServoAction wristLR = new ServoAction((byte) 2, s2ServoAngle);

		//????????????
		short s3ServoAngle = LandmarkUtil.getS3ServoMove(
				(indexFingerTip.getZ() + thumbTip.getZ()) / 2 - middleFingerMCP.getZ(),
				(indexFingerTip.getY() + thumbTip.getY()) / 2 - middleFingerMCP.getY());
		ServoAction wristUD = new ServoAction((byte) 3, s3ServoAngle);

		//????????????????????????????????????
		//?????????????????????
		LandmarkProto.NormalizedLandmark normalizedWrist = result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
		LandmarkProto.NormalizedLandmark normalizedMiddleFingerMCP = result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.MIDDLE_FINGER_MCP);
		//??????????????????????????????????????????
		float normalizedDistance = LandmarkUtil.normalizedDistanceOfTwoPoint(normalizedWrist, normalizedMiddleFingerMCP);
		//????????????????????????????????????????????????
		float distance = (90 - 200 * normalizedDistance);
		//??????????????????????????????????????????
		float centralX = ((normalizedWrist.getX() + normalizedMiddleFingerMCP.getX() - 1) * 40);
		float centralY = (float) ((1.5 - normalizedWrist.getY() - normalizedMiddleFingerMCP.getY()) * 40);
		short[] d = LandmarkUtil.getS456ServoMove(70 - distance, centralY, centralX);
/*		ServoAction servo4 = new ServoAction((byte) 4, d[1]);
		ServoAction servo5 = new ServoAction((byte) 5, d[0]);*/
		Log.i(
				TAG,
				String.format(
						"Media??????:A= %s B= %s A= %f B= %f D=%f",
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

	//??????

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

	//???????????????????????????
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

	//???????????????????????????
	public void setupSetClick() {
		ImageButton loadSetButton = findViewById(R.id.set_btn);
		loadSetButton.setOnClickListener(v -> {
			BLEManager.getInstance().stop();
			MainActivity.super.onBackPressed();
			android.os.Process.killProcess(android.os.Process.myPid());
		});
	}

	//???????????????????????????
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

	//???????????????????????????
	public void setupNetworkClick() {
		ImageButton loadBluetoothButton = findViewById(R.id.network_btn);
		loadBluetoothButton.setOnClickListener(v -> {
			//??????????????????????????????
			if (!((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo().isAvailable()) {
				LogUtil.showToast(this, "??????????????????????????????");
			}
			if (networkIsConnected) {
				PromptDialog.create(getBaseContext(), getFragmentManager(), getString(R.string.disconnect_tips_title),
						getString(R.string.disconnect_network_tips_connect), (dialog, which) -> {
							if (DialogInterface.BUTTON_POSITIVE == which) {
								//TODO ????????????
							}
						});
			} else {
				NetworkDialog.createDialog(getFragmentManager(), this);
			}
		});
	}

	/**
	 * ????????????????????????
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

	class MsgCallBack implements Handler.Callback {//????????????

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
