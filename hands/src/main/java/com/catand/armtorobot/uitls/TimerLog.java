package com.catand.armtorobot.uitls;

import android.util.Log;

/**
 * 计时的日志工具
 * Created by hejie on 2015/8/19.
 */
public class TimerLog {

	private static long currentTime = System.currentTimeMillis();

	public static void logTime(String tag) {
		long millis = System.currentTimeMillis();
		Log.i("TimerLog", tag + " cost " + (millis - currentTime) + " ms");
		currentTime = millis;
	}
}
