package com.catand.armtorobot.uitls;

import android.os.Handler;

import java.lang.ref.WeakReference;

public class BaseUIHandler<T> extends Handler {

	/**
	 * UI对象的弱引用
	 */
	protected WeakReference<T> reference;

	public BaseUIHandler(T target) {
		reference = new WeakReference<T>(target);
	}

	/**
	 * 获取T的实例
	 */
	protected T get() {
		return reference.get();
	}

}
