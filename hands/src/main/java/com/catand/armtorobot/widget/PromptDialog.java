package com.catand.armtorobot.widget;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.catand.armtorobot.R;

/**
 * 确认对话框
 * Created by andy on 2016/6/28.
 */
public class PromptDialog extends BaseDialog {

	private CharSequence content;

	public static DialogFragment create(Context context, FragmentManager fm, String title, CharSequence content, OnClickListener onClickListener) {
		return create(context, fm, title, content, context.getString(R.string.dialog_cancel),
				context.getString(R.string.dialog_yes), onClickListener);
	}

	public static DialogFragment create(Context context, FragmentManager fm, String title, CharSequence content,
	                                    String leftText, String rightText, OnClickListener onClickListener) {
		PromptDialog dialog = new PromptDialog();
		dialog.context = context;
		Bundle args = buildArgs(title, leftText, rightText, true);
		args.putCharSequence("content", content);
		dialog.setArguments(args);
		dialog.setOnClickListener(onClickListener);
		dialog.show(fm, "PromptDialog");
		return dialog;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.content = getArguments().getCharSequence("content", "");
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.layout_dialog_prompot, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		TextView contentTV = view.findViewById(R.id.dialog_content);
		if (!TextUtils.isEmpty(content)) {
			contentTV.setText(content);
		}
		contentTV.setMovementMethod(LinkMovementMethod.getInstance());
	}
}
