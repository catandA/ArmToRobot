package com.catand.armtorobot.model;

/*
	舵机 ID 号
	角度位置低八位
	角度位置高八位

	舵机1 爪部开合舵机
	开-合
	500-1500

	舵机2 手腕转动舵机
	逆时针-顺时针
	500-2500

	舵机3 手腕上下舵机
	上-下
	500-2500

	舵机4 小臂上下舵机
	上-下
	500-2000

	舵机5 大臂上下舵机
	上-下
	500-2500

	舵机6 总体转动舵机
	顺时针-逆时针
	(向右转-向左转)
	500-2500
 */

public class ServoAction {
	private byte id;
	private short angle;

	public ServoAction(byte id, short angle) {
		this.id = id;
		this.angle = angle;
	}

	public byte getId() {
		return id;
	}

	public void setId(byte id) {
			this.id = id;
	}

	public short getAngle() {
		return angle;
	}

	public void setAngle(short angle) {
		this.angle = angle;
	}
}
