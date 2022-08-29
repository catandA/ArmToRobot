package com.catand.armtorobot.uitls;
/*
通信协议

帧头          数据长度     指令     参数
0x55 0x55    Length      Cmd     Prm 1…Prm N
帧头：连续收到两个 0x55 ,表示有数据包到达。
数据长度：等于除帧头两个字节外的待发送的数据的字节数，即 参数个数+2，
指令：各种控制指令
参数：除指令外需要补充的控制信息。

6. 指令名CMD_FULL_ACTION_RUN 指令值 6 数据长度5：
说明：动作组运行，如果参数次数为无限次，则次参数值为 0
参数 1：要运行的动作组的编号
参数 2：动作组要执行的次数低八位
参数 3：动作组要执行的次数高八位

7. 指令名CMD_FULL_ACTION_STOP 指令值 7 数据长度2：
说明：停止正在运行的动作。
参数：无参数

8. 指令名 CMD_FULL_ACTION_ERASE 指令值 8 数据长度 3：
说明：将下载到控制板的动作组擦除
参数 1：（保留）
返回：控制板返回不带参数的指令

25. 指令名 CMD_BLE_SERVO_DOWNLOAD 指令值 25 数据长度 N：
说明：通过手机蓝牙下载动作组，一帧一帧的下载，该动作组有多少帧就会下载
多少次。
数据长度 N=下载舵机的个数×3+8
参数 1：要下载到的动作组编号
参数 2：该动作组的总帧数
参数 3：第几帧数据
参数 4：要下载舵机的个数
参数 5：时间低八位
参数 6：时间高八位
参数 7：舵机 ID 号
参数 8：角度位置低八位
参数 9：角度位置高八位
参数......：格式与参数 7,8,9 相同，不同 ID 的角度位置。
每下载一帧数据，板子都会返回数据，返回数据的指令值是相同的，不过是不带
参数的指令包。
*/

import com.catand.armtorobot.MainActivity;
import com.catand.armtorobot.model.ByteCommand;
import com.catand.armtorobot.model.ServoAction;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class CmdUtil {
	public final static byte HEAD = 0x55;
	public final static byte CMD_MULT_SERVO_MOVE = 0x03;
	public final static byte CMD_FULL_ACTION_RUN = 0x06;
	public final static byte CMD_FULL_ACTION_STOP = 0x07;
	public final static byte CMD_FULL_ACTION_ERASE = 0x08;
	public final static byte CMD_BLE_SERVO_DOWNLOAD = 0x19;
	private static DatagramSocket socket;
	private static String uRL = "103.91.209.218";
	private static int port = 13333;

	/*
	3. 指令名 CMD_MULT_SERVO_MOVE 指令值 3 数据长度 N：
	说明：控制多个舵机的转动，数据长度 N=控制舵机的个数×3+5
	参数 1：要控制舵机的个数
	参数 2：时间低八位
	参数 3：时间高八位
	参数 4：舵机 ID 号
	参数 5：角度位置低八位
	参数 6：角度位置高八位
	参数......：格式与参数 4,5,6 相同，控制不同 ID 的角度位置。
	 */

	public static byte[] CMD_MULT_SERVO_MOVE(short time, ServoAction... servoActions) {
		//运动舵机数量
		int servoNum = servoActions.length;

		//构造数据包,使用List<Byte>方便添加
		ArrayList<Byte> byteArray = new ArrayList<>();
		//数据包头部
		byteArray.add(HEAD);
		byteArray.add(HEAD);
		//数据长度
		byteArray.add((byte) (servoNum * 3 + 5));
		//指令
		byteArray.add(CMD_MULT_SERVO_MOVE);
		//运动舵机数量
		byteArray.add((byte) servoNum);
		//运动时间
		byte[] times = shortToByte(time);
		byteArray.add(times[0]);
		byteArray.add(times[1]);
		//舵机运动信息
		for (ServoAction servoAction : servoActions) {
			byteArray.add(servoAction.getId());
			byte[] angle = shortToByte(servoAction.getAngle());
			byteArray.add(angle[0]);
			byteArray.add(angle[1]);
		}


		//把List<Byte>转换回byte
		byte[] bytes = new byte[byteArray.size()];
		int j = 0;
		for (Byte b : byteArray) {
			bytes[j++] = b;
		}
		return bytes;
	}

	/**
	 * 把舵机移动指令发送到蓝牙
	 */
	public static void sendCMDToBluetooth(byte[] cMDBytes) {
		ByteCommand.Builder builder = new ByteCommand.Builder();
		builder.addCommand(cMDBytes, 30);
		MainActivity.bleManager.send(builder.createCommands());
	}

	/**
	 * 把舵机移动指令发送到网络
	 */
	public static void sendCMDToNetwork(byte[] cMDBytes) throws IOException {
		//单例模式
		if (socket == null) {
			socket = new DatagramSocket();
		}

		//创建一个数据包对象封装数据
		DatagramPacket packet = new DatagramPacket(cMDBytes, cMDBytes.length, InetAddress.getByName(uRL), port);

		//发送数据出去
		socket.send(packet);

	}

	/**
	 * 关闭网络链接
	 */
	public static void closeDatagramSocket(){
		if (socket == null){
			return;
		}
		socket.close();
	}

	//short拆分成byte[],低位为[0],高位为[1]
	public static byte[] shortToByte(short s) {
		byte[] bytes = new byte[2];
		bytes[0] = (byte) (s & 0xFF);
		bytes[1] = (byte) ((s >> 8) & 0xFF);
		return bytes;
	}

	public static String getuRL() {
		return uRL;
	}

	public static void setuRL(String uRL) {
		CmdUtil.uRL = uRL;
	}

	public static int getPort() {
		return port;
	}

	public static void setPort(int port) {
		if (port<0||port>65535){
			return;
		}
		CmdUtil.port = port;
	}
}
