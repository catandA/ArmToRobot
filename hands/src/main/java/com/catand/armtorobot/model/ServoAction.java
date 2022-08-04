package com.catand.armtorobot.model;

/*
	舵机 ID 号
	角度位置低八位
	角度位置高八位
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
