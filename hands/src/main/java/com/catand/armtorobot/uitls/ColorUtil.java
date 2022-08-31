package com.catand.armtorobot.uitls;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

public class ColorUtil {

	/**
	 * 获取应用程序的强调色
	 */
	public static int getAccentColor(Context context) {
		int accentColor;
		TypedValue accentColorTypedValue = new TypedValue();
		try {
			context.getTheme().resolveAttribute(android.R.attr.colorAccent, accentColorTypedValue, true);
			accentColor = accentColorTypedValue.data;
		} catch (Exception e) {
			try {
				int colorPrimaryId = context.getResources().getIdentifier("colorAccent", "attr", context.getPackageName());
				if (colorPrimaryId != 0) {
					context.getTheme().resolveAttribute(colorPrimaryId, accentColorTypedValue, true);
					accentColor = accentColorTypedValue.data;
				} else {
					throw new RuntimeException("colorAccent not found");
				}
			} catch (Exception e1) {
				accentColor = Color.BLUE;
			}
		}
		return accentColor;
	}

	/**
	 * 获取应用程序的主要色
	 */
	public static int getPrimaryColor(Context context) {
		int primaryColor;
		TypedValue primaryColorTypedValue = new TypedValue();
		try {
			context.getTheme().resolveAttribute(android.R.attr.colorPrimary, primaryColorTypedValue, true);
			primaryColor = primaryColorTypedValue.data;
		} catch (Exception e) {
			try {
				int colorPrimaryId = context.getResources().getIdentifier("colorPrimary", "attr", context.getPackageName());
				if (colorPrimaryId != 0) {
					context.getTheme().resolveAttribute(colorPrimaryId, primaryColorTypedValue, true);
					primaryColor = primaryColorTypedValue.data;
				} else {
					throw new RuntimeException("colorPrimary not found");
				}
			} catch (Exception e1) {
				primaryColor = Color.BLUE;
			}
		}
		return primaryColor;
	}


	/**
	 * 获取应用程序的主要深色
	 */
	public static int getPrimaryDarkColor(Context context) {
		int primaryDarkColor;
		TypedValue primaryColorDarkTypedValue = new TypedValue();
		try {
			context.getTheme().resolveAttribute(android.R.attr.colorPrimaryDark, primaryColorDarkTypedValue, true);
			primaryDarkColor = primaryColorDarkTypedValue.data;
		} catch (Exception e) {
			try {
				int colorPrimaryId = context.getResources().getIdentifier("colorPrimaryDark", "attr", context.getPackageName());
				if (colorPrimaryId != 0) {
					context.getTheme().resolveAttribute(colorPrimaryId, primaryColorDarkTypedValue, true);
					primaryDarkColor = primaryColorDarkTypedValue.data;
				} else {
					throw new RuntimeException("colorPrimaryDark not found");
				}
			} catch (Exception e1) {
				primaryDarkColor = Color.BLUE;
			}
		}
		return primaryDarkColor;
	}

	/**
	 * 判断颜色的明暗
	 * @param color the target color
	 * @return true if is light color,else false if dark color
	 */
	public static boolean isLight(int color) {
		return Math.sqrt(
				Color.red(color) * Color.red(color) * .241 +
						Color.green(color) * Color.green(color) * .691 +
						Color.blue(color) * Color.blue(color) * .068) > 130;
	}

	public static int getBaseColor(int color) {
		if (isLight(color)) {
			return Color.BLACK;
		}
		return Color.WHITE;
	}
}
