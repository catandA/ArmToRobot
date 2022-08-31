package com.catand.armtorobot.uitls;

import android.util.Log;

/**
 * 计时的日志工具
 */
public class TimerLog {

	private static long currentTime = System.currentTimeMillis();

	public static void logTime(String tag) {
		long millis = System.currentTimeMillis();
		Log.i("TimerLog", tag + " cost " + (millis - currentTime) + " ms");
		currentTime = millis;
	}
}
