package com.catand.armtorobot.uitls;

import com.google.mediapipe.formats.proto.LandmarkProto;

public class LandmarkUtil {
	//大小臂长度
	public final static float ARM_B = 10.4f;
	public final static float ARM_S = 8.9f;

	/**
	 * 计算两点的世界坐标距离
	 */
	public static float distanceOfTwoPoint(LandmarkProto.Landmark a, LandmarkProto.Landmark b) {
		return
				(float) Math.sqrt(
						(a.getX() - b.getX()) * (a.getX() - b.getX()) +
								(a.getY() - b.getY()) * (a.getY() - b.getY()) +
								(a.getZ() - b.getZ()) * (a.getZ() - b.getZ()));
	}

	/**
	 * 计算两点的归一化坐标距离
	 */
	public static float normalizedDistanceOfTwoPoint(LandmarkProto.NormalizedLandmark a, LandmarkProto.NormalizedLandmark b) {
		return
				(float) Math.sqrt(
						(a.getX() - b.getX()) * (a.getX() - b.getX()) +
								(a.getY() - b.getY()) * (a.getY() - b.getY()) +
								(a.getZ() - b.getZ()) * (a.getZ() - b.getZ()));
	}

	/**
	 * 计算两点的归一化坐标距离
	 */
	public static float normalizedDistanceOfTwoPoint(float ax, float ay, float az, float bx, float by, float bz) {
		return
				(float) Math.sqrt(
						(ax - bx) * (ax - bx) +
								(ay - by) * (ay - by) +
								(az - bz) * (az - bz));
	}

	/**
	 * 由食指尖和拇指尖的世界坐标算出舵机1的运动角度
	 */
	public static short getS1ServoMove(LandmarkProto.Landmark a, LandmarkProto.Landmark b) {
		short angle = (short) (1800 - (12000 * LandmarkUtil.distanceOfTwoPoint(a, b)));
		if (angle > 1500) {
			return 1500;
		}
		if (angle < 500) {
			return 500;
		}
		return angle;
	}

	/**
	 * 由手腕的世界坐标算出舵机2的运动角度
	 */
	public static short getS2ServoMove(float x, float y) {
		short angle = (short) (((Math.atan2(y, x) / Math.PI) * 4000 / 1.8) - 200);
		if (angle < -500) {
			if (angle + 4500 > 2500) {
				return 2500;
			}
			return (short) (angle + 4500);
		}
		if (angle < 500) {
			return 500;
		}
		return angle;
	}

	/**
	 * 由食指尖和拇指尖的中点-中指根部得到的世界坐标算出舵机3的运动角度
	 */
	public static short getS3ServoMove(float z, float y) {
		short angle = (short) (((Math.atan2(y, z) / Math.PI) * 4000 / 1.8));
		if (angle < 0) {
			return (short) (-angle - 400);
		}
		return (short) (3900 - angle);
	}

	public static short[] getS456ServoMove(float x, float y, float z) {
		double b = normalizedDistanceOfTwoPoint(x, y, z, 0, 0, 0);
		//大臂
		double angleA = (Math.acos(((b * b) + (ARM_B * ARM_B) - (ARM_S * ARM_S)) / 2 / b / ARM_B) + Math.atan(y / x));
		//小臂
		double angleB = (Math.PI - (Math.acos(((ARM_S * ARM_S) + (ARM_B * ARM_B) - (b * b)) / 2 / ARM_S / ARM_B)));

		angleA = (Math.toDegrees(angleA) / 180 * 2000)+500;
		angleB = (Math.toDegrees(angleB) / 180 * 2000)+500;
		if (angleA == 0 || angleB == 0) {
			return new short[]{0, 0};
		}
		return new short[]{(short) angleA, (short) angleB};
	}

}
