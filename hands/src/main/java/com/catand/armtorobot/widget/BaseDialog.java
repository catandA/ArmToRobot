package com.catand.armtorobot.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.catand.armtorobot.R;

/**
 * 对话框基础类
 */
public class BaseDialog extends DialogFragment implements DialogInterface, View.OnClickListener {

	protected Context context;
	protected String title;
	protected String leftBtnText;
	protected String rightBtnText;
	protected boolean autoCancel = true;

	protected OnClickListener onClickListener;

	public OnClickListener getOnClickListener() {
		return onClickListener;
	}

	public void setOnClickListener(OnClickListener onClickListener) {
		this.onClickListener = onClickListener;
	}

	/**
	 * 创建一个包含确认对话框基本信息的Bundle
	 * @param title 标题
	 * @param leftBtnText 左按钮文本
	 * @param rightBtnText 右按钮文本
	 * @param autoCancel 自动取消
	 */
	protected static Bundle buildArgs(String title, String leftBtnText, String rightBtnText, boolean autoCancel) {
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putString("leftBtnText", leftBtnText);
		args.putString("rightBtnText", rightBtnText);
		args.putBoolean("autoCancel", autoCancel);
		return args;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(STYLE_NO_TITLE, getTheme());
		Bundle args = getArguments();
		if (args != null) {
			title = args.getString("title", "");
			leftBtnText = args.getString("leftBtnText", "");
			rightBtnText = args.getString("rightBtnText", "");
			autoCancel = args.getBoolean("autoCancel", true);
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		TextView titleTV = view.findViewById(R.id.dialog_title);
		View line = view.findViewById(R.id.dialog_divider);
		if (!TextUtils.isEmpty(title)) {
			titleTV.setText(title);
		}
		if (!TextUtils.isEmpty(leftBtnText)) {
			Button leftBtn = view.findViewById(R.id.dialog_left_btn);
			line.setVisibility(View.VISIBLE);
			leftBtn.setVisibility(View.VISIBLE);
			leftBtn.setText(leftBtnText);
			leftBtn.setOnClickListener(this);
		}
		if (!TextUtils.isEmpty(rightBtnText)) {
			Button rightBtn = view.findViewById(R.id.dialog_right_btn);
			line.setVisibility(View.VISIBLE);
			rightBtn.setVisibility(View.VISIBLE);
			rightBtn.setText(rightBtnText);
			rightBtn.setOnClickListener(this);
		}
	}

	@Override
	public void onClick(View v) {
		//资源ID不是编译期常量，不能用 switch
		int id = v.getId();
		if (id == R.id.dialog_left_btn) {
			if (onClickListener != null)
				onClickListener.onClick(BaseDialog.this, DialogInterface.BUTTON_NEGATIVE);
		} else if (id == R.id.dialog_right_btn) {
			if (onClickListener != null)
				onClickListener.onClick(BaseDialog.this, DialogInterface.BUTTON_POSITIVE);
		}
		if (autoCancel)
			dismissAllowingStateLoss();
	}

	@Override
	public void cancel() {

	}
}
