package com.catand.armtorobot.widget;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.catand.armtorobot.R;
import com.catand.armtorobot.uitls.BluetoothUtils;
import com.catand.armtorobot.uitls.LogUtil;
import com.google.android.material.textfield.TextInputEditText;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

/**
 * 蓝牙搜索对话框
 */
@SuppressLint("MissingPermission")
public class NetworkDialog extends DialogFragment implements OnClickListener, OnItemClickListener {

	private static final String TAG = NetworkDialog.class.getSimpleName();

	/**
	 * 搜索时间，60s
	 */
	private static final int SCAN_TIMEOUT = 60000;

	private TextView titleTV;
	private CircularProgressView progressView;
	private NetworkDataAdapter mAdapter;
	private TextInputEditText addressInputView;

	/**
	 * 取本机蓝牙适配器，不支持蓝牙时返回 null。
	 */
	private BluetoothAdapter adapter() {
		Context context = getContext();
		return context == null ? null : BluetoothUtils.getAdapter(context);
	}

	/**
	 * 取 BLE 扫描器，适配器或扫描器不可用时返回 null。
	 */
	private BluetoothLeScanner scanner() {
		BluetoothAdapter adapter = adapter();
		return adapter == null ? null : adapter.getBluetoothLeScanner();
	}

	/**
	 * 读取已配对设备。
	 *
	 * <p>Android 12 起该调用需要 BLUETOOTH_CONNECT，未授予时会抛 SecurityException；
	 * 返回空集合而不是让对话框崩掉。
	 */
	private Set<BluetoothDevice> bondedDevices() {
		BluetoothAdapter adapter = adapter();
		if (adapter == null) {
			return Collections.emptySet();
		}
		try {
			return adapter.getBondedDevices();
		} catch (SecurityException e) {
			LogUtil.w(TAG, "缺少蓝牙权限，无法读取已配对设备: " + e);
			return Collections.emptySet();
		}
	}

	private static OnRobotSelectedListener onRobotSelectedListener;
	private boolean scanning = false;
	private Handler mHandler;
	private ScanCallback scanCallback = new ScanCallback() {

		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			BluetoothDevice device = result.getDevice();
			LogUtil.i(TAG, "found device : " + device.getName() + ", address = " + device.getAddress());
			if (device.getBondState() != BluetoothDevice.BOND_BONDED)
				mAdapter.add(device);
		}
	};

	/**
	 * 搜索BLE蓝牙设备
	 */
	public void scanBLEDevice() {
		BluetoothLeScanner bleScanner = scanner();
		if (mHandler != null) {
			if (bleScanner == null) {
				LogUtil.w(TAG, "bleScanner is null, skip scan");
				return;
			}
			mHandler.postDelayed(this::stopScan, SCAN_TIMEOUT);
			try {
				bleScanner.startScan(scanCallback);
			} catch (SecurityException e) {
				LogUtil.w(TAG, "缺少蓝牙权限，无法开始扫描: " + e);
				return;
			}
			titleTV.setText(R.string.dialog_searching);
			progressView.setVisibility(View.VISIBLE);
			progressView.resetAnimation();
			scanning = true;
		}
	}

	/**
	 * 停止搜索
	 */
	public void stopScan() {
		BluetoothLeScanner bleScanner = scanner();
		if (scanning) {
			if (bleScanner != null) {
				try {
					bleScanner.stopScan(scanCallback);
				} catch (SecurityException e) {
					LogUtil.w(TAG, "缺少蓝牙权限，无法停止扫描: " + e);
				}
			} else {
				LogUtil.w(TAG, "bleScanner is null, skip stopScan");
			}
			titleTV.setText(R.string.dialog_search_finished);
			progressView.setVisibility(View.GONE);
			titleTV.setText(R.string.dialog_search_finished);
			scanning = false;
		}
	}

	public static void createDialog(FragmentManager fragmentManager, OnRobotSelectedListener onRobotSelectedListener) {
		NetworkDialog.onRobotSelectedListener = onRobotSelectedListener;
		NetworkDialog dialog = new NetworkDialog();
		dialog.show(fragmentManager, "serverConnectDialog");
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(STYLE_NO_TITLE, getTheme());
		mHandler = new Handler();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		return inflater.inflate(R.layout.network_dialog, container, false);

	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		titleTV = view.findViewById(R.id.dialog_title);
		addressInputView = view.findViewById(R.id.server_address_input);
		ListView listView = view.findViewById(R.id.device_list);
		progressView = view.findViewById(R.id.dialog_search_progress);
		Button cancelBtn = view.findViewById(R.id.dialog_left_btn);
		Button restartBtn = view.findViewById(R.id.dialog_right_btn);

		mAdapter = new NetworkDataAdapter(getActivity());
		addressInputView.setText(R.string.default_address);
		titleTV.setText(R.string.dialog_searching);
		listView.setAdapter(mAdapter);
		listView.setOnItemClickListener(this);
		cancelBtn.setOnClickListener(this);
		restartBtn.setOnClickListener(this);

		scanBLEDevice();
	}

	/**
	 * 当此View被关闭时,停止扫描
	 */
	public void onDestroyView() {
		super.onDestroyView();
		stopScan();
	}

	@Override
	public void onClick(View v) {
		//资源ID不是编译期常量，不能用 switch
		int id = v.getId();
		if (id == R.id.dialog_right_btn) {
			stopScan();
			mAdapter.clear();
			scanBLEDevice();
		} else if (id == R.id.dialog_left_btn) {
			dismissAllowingStateLoss();
		}
	}

	class NetworkDataAdapter extends BaseAdapter {

		/**
		 * 已扫描到的蓝牙设备列表
		 */
		private ArrayList<BluetoothDevice> devices;

		private Context context;

		public NetworkDataAdapter(Context context) {
			this.context = context;
			Set<BluetoothDevice> pairedDevices = bondedDevices();
			devices = new ArrayList<>();
			//如果有配对设备
			if (pairedDevices.size() > 0) {
				//循环添加所有已配对设备
				devices.addAll(pairedDevices);
			}
		}

		public void add(BluetoothDevice device) {
			if (!devices.contains(device)) {
				this.devices.add(device);
				notifyDataSetChanged();
			}
		}

		/**
		 * 清除所有未配对设备
		 */
		public void clear() {
			devices.clear();
			Set<BluetoothDevice> pairedDevices = bondedDevices();
			devices = new ArrayList<>();
			//如果有配对设备
			if (pairedDevices.size() > 0) {
				//循环添加所有已配对设备
				devices.addAll(pairedDevices);
			}
			//刷新视图
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			ViewHolder holder = new ViewHolder();

			if (view == null) {
				LayoutInflater inflater = LayoutInflater.from(context);
				view = inflater.inflate(R.layout.item_search_device, parent,
						false);
				holder.titleView = view.findViewById(R.id.item_device_title);
				holder.contentView = view.findViewById(R.id.item_device_content);
				view.setTag(holder);
			} else {
				holder = (ViewHolder) view.getTag();
			}
			BluetoothDevice device = devices.get(position);
			String title;
			if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
				title = getString(R.string.dialog_search_item_bonded, device.getName());
			} else {
				title = device.getName();
			}
			holder.titleView.setText(title);
			holder.contentView.setText(device.getAddress());
			return view;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public BluetoothDevice getItem(int position) {
			return devices.get(position);
		}

		@Override
		public int getCount() {
			return devices.size();
		}

		class ViewHolder {
			TextView titleView;
			TextView contentView;
		}
	}

	public interface OnRobotSelectedListener {
		void onDeviceSelected(BluetoothDevice device);
	}

	/**
	 * @see OnItemClickListener#onItemClick(AdapterView,
	 * View, int, long)
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (onRobotSelectedListener != null) {
			BluetoothDevice device = mAdapter.getItem(position);
			onRobotSelectedListener.onDeviceSelected(device);
		}
		dismissAllowingStateLoss();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		BluetoothAdapter adapter = adapter();
		if (adapter == null) {
			return;
		}
		try {
			adapter.cancelDiscovery();
		} catch (SecurityException e) {
			LogUtil.w(TAG, "缺少蓝牙权限，无法取消经典蓝牙搜索: " + e);
		}
	}
}
