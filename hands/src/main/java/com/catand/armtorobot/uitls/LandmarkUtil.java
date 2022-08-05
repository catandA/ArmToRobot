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

	public static short distanceToAngle(float distance) {
		short angle = (short) (1800-(12000*distance));
		if(angle>1500){
			return 1500;
		}else if (angle<500){
			return 500;
		}
		return angle;
	}

}
