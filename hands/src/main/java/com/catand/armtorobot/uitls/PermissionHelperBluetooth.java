package com.catand.armtorobot.uitls;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 管理相机权限请求和处理.
 */
public class PermissionHelperBluetooth {
	private static final String TAG = "PermissionHelper";

	private static final String BLUETOOTH_SCAN_PERMISSION = Manifest.permission.BLUETOOTH_SCAN;
	private static final String BLUETOOTH_CONNECT_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT;
	private static final String ACCESS_FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;
	private static final String ACCESS_COARSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION;

	private static final int REQUEST_CODE = 0;

	public static boolean permissionsGranted(Activity context, String[] permissions) {
		for (String permission : permissions) {
			int permissionStatus = ContextCompat.checkSelfPermission(context, permission);
			if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	public static void checkAndRequestPermissions(Activity context, String[] permissions) {
		if (!permissionsGranted(context, permissions)) {
			ActivityCompat.requestPermissions(context, permissions, REQUEST_CODE);
		}
	}

	/**
	 * 由上下文调用以检查是否已授予蓝牙权限.
	 */
	public static boolean bluetoothPermissionsGranted(Activity context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return permissionsGranted(context, new String[]{BLUETOOTH_SCAN_PERMISSION})
					&& permissionsGranted(context, new String[]{BLUETOOTH_CONNECT_PERMISSION});
		}
		return locationtoothPermissionsGranted(context);
	}

	/**
	 * 由上下文调用以检查是否已授予蓝牙权限，如果没有，则请求它们.
	 */
	public static void checkAndRequestBluetoothPermissions(Activity context) {
		Log.d(TAG, "checkAndRequestBluetoothPermissions");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			checkAndRequestPermissions(context, new String[]{BLUETOOTH_CONNECT_PERMISSION});
			checkAndRequestPermissions(context, new String[]{BLUETOOTH_SCAN_PERMISSION});
		}else {
			checkAndRequestLocationPermissions(context);
		}
	}

	/**
	 * 由上下文调用以检查是否已授予定位权限.
	 */
	public static boolean locationtoothPermissionsGranted(Activity context) {
		return permissionsGranted(context, new String[]{ACCESS_FINE_LOCATION_PERMISSION}) && permissionsGranted(context, new String[]{ACCESS_COARSE_LOCATION_PERMISSION});
	}

	/**
	 * 由上下文调用以检查是否已授予定位权限，如果没有，则请求它们.
	 */
	public static void checkAndRequestLocationPermissions(Activity context) {
		Log.d(TAG, "checkAndRequestLocationPermissions");
		checkAndRequestPermissions(context, new String[]{ACCESS_FINE_LOCATION_PERMISSION});
		checkAndRequestPermissions(context, new String[]{ACCESS_COARSE_LOCATION_PERMISSION});
	}

	/**
	 * 权限请求完成时由上下文调用.
	 */
	public static void onRequestPermissionsResult(
			int requestCode, String[] permissions, int[] grantResults) {
		Log.d(TAG, "onRequestPermissionsResult");
		if (permissions.length > 0 && grantResults.length != permissions.length) {
			Log.d(TAG, "Permission denied.");
			return;
		}
		for (int i = 0; i < grantResults.length; ++i) {
			if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
				Log.d(TAG, permissions[i] + " permission granted.");
			}
		}
		// 注意：当权限准备好时，我们不需要任何特殊的回调，
		// 因为使用此帮助类的活动可以在 onResume() 中包含代码，
		// 该代码在权限对话框关闭后调用。代码可以根据权限是否可以通过权限授予（活动）进行分支。
	}
}