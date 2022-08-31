package com.catand.armtorobot.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ByteCommand {
	ByteBuffer m_commandArray;

	long delay;

	public static class Builder {

		private List<ByteCommand> commandList;

		public Builder() {
			this.commandList = new ArrayList<>();
		}

		public Builder addCommand(byte[] commandArray, long delay) {
			commandList.add(new ByteCommand(commandArray, delay));
			return this;
		}

		public List<ByteCommand> createCommands() {
			return commandList;
		}
	}

	public ByteCommand() {
	}

	public ByteCommand(byte[] commandArray) {
		m_commandArray = ByteBuffer.wrap(commandArray);
		this.delay = 0;
	}

	public ByteCommand(byte[] commandArray, long delay) {
		m_commandArray = ByteBuffer.wrap(commandArray);
		this.delay = delay;
	}

	public ByteBuffer getCommandByteBuffer() {
		return m_commandArray;
	}

	public void setCommandBuffer(byte[] commandArray) {
		m_commandArray = ByteBuffer.wrap(commandArray);
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}
}
