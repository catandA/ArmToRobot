package com.catand.armtorobot.uitls;

import com.google.mediapipe.formats.proto.LandmarkProto;

public class LandmarkUtil {
	public static float distanceOfTwoPoint(LandmarkProto.Landmark a, LandmarkProto.Landmark b) {
		return
				(float) Math.sqrt(
						(a.getX() - b.getX()) * (a.getX() - b.getX()) +
								(a.getY() - b.getY()) * (a.getY() - b.getY()) +
								(a.getZ() - b.getZ()) * (a.getZ() - b.getZ()));
	}

	public static short distanceToS1ServoMove(LandmarkProto.Landmark a, LandmarkProto.Landmark b) {
		short angle = (short) (1800 - (12000 * LandmarkUtil.distanceOfTwoPoint(a,b)));
		if (angle > 1500) {
			return 1500;
		}
		if (angle < 500) {
			return 500;
		}
		return angle;
	}

	public static short coordinateToS2ServoMove(float x, float y) {
		short angle = (short) (((Math.atan2(y, x) / Math.PI) * 4000 / 1.8)-200);
		if (angle < -500) {
			if (angle+4500>2500){
				return 2500;
			}
			return (short) (angle+4500);
		}
		if (angle < 500) {
			return 500;
		}
		return angle;
	}
	public static short coordinateToS3ServoMove(float z, float y) {
		short angle = (short) (((Math.atan2(y, z) / Math.PI) * 4000 / 1.8));
		if (angle<0){
			return (short) (-angle-400);
		}
		return (short) (3900-angle);
	}

}
