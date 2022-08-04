package com.catand.armtorobot.widget;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.catand.armtorobot.R;

/**
 * 对话框基础类
 * Created by andy on 2016/6/28.
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

	public BaseDialog() {
	}

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
		switch (v.getId()) {
			case R.id.dialog_left_btn:
				if (onClickListener != null)
					onClickListener.onClick(BaseDialog.this, DialogInterface.BUTTON_NEGATIVE);
				break;
			case R.id.dialog_right_btn:
				if (onClickListener != null)
					onClickListener.onClick(BaseDialog.this, DialogInterface.BUTTON_POSITIVE);
				break;
		}
		if (autoCancel)
			dismissAllowingStateLoss();
	}

	@Override
	public void cancel() {

	}
}
