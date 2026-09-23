package com.catand.armtorobot.uitls;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 蓝牙相关工具类
 */
public class BluetoothUtils {

	private static final String TAG = BluetoothUtils.class.getSimpleName();

	/**
	 * 获取本机蓝牙适配器，不支持蓝牙时返回 null。
	 *
	 * <p>不要用 BluetoothAdapter.getDefaultAdapter()，该接口自 API 33 起废弃。
	 */
	public static BluetoothAdapter getAdapter(Context context) {
		if (context == null) {
			return null;
		}
		BluetoothManager manager =
				(BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
		return manager == null ? null : manager.getAdapter();
	}

	public static boolean isSupport(BluetoothAdapter bluetoothAdapter) {
		if (bluetoothAdapter == null) {
			LogUtil.w(TAG, "device do not support bluetooth!");
			return false;
		}
		return true;
	}

	public static boolean removePaired(BluetoothDevice device) {
		try {
			Method removeMethod = device.getClass().getMethod("removeBond");
			return (Boolean) removeMethod.invoke(device);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean setPin(BluetoothDevice device, String pin) {
		try {
			Method setPin = device.getClass().getMethod("setPin", byte[].class);
			return (Boolean) setPin.invoke(device, new Object[]{pin.getBytes()});
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean createBound(BluetoothDevice device) {
		try {
			Method createBond = device.getClass().getMethod("createBond");
			return (Boolean) createBond.invoke(device);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static boolean cancelPairingUserInput(BluetoothDevice device) {
		try {
			Method cancelPairingUserInput = device.getClass().getMethod("cancelPairingUserInput");
			return (Boolean) cancelPairingUserInput.invoke(device);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
}
