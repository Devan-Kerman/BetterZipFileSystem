package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;

class SeekableByteChannelWrapper implements SeekableByteChannel {
	final SeekableByteChannel channel;
	boolean isClosed;
	long pos, size = -1;
	
	public SeekableByteChannelWrapper(SeekableByteChannel channel) {
		this.channel = channel;
	}
	
	@Override
	public int read(ByteBuffer dst) throws IOException {
		synchronized(this.channel) {
			int read = this.channel.position(this.pos).read(dst);
			this.pos = this.channel.position();
			return read;
		}
	}
	
	@Override
	public int write(ByteBuffer src) throws IOException {
		synchronized(this.channel) {
			int write = this.channel.position(this.pos).write(src);
			this.pos = this.channel.position();
			return write;
		}
	}
	
	@Override
	public long position() throws IOException {
		return this.pos;
	}
	
	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		this.pos = newPosition;
		return this;
	}
	
	@Override
	public long size() throws IOException {
		long size = this.size;
		return size == -1 ? this.channel.size() : size;
	}
	
	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		this.size = size;
		return this;
	}
	
	@Override
	public boolean isOpen() {
		return !this.isClosed;
	}
	
	@Override
	public void close() throws IOException {
		if(!this.isClosed) {
			this.isClosed = true;
		}
	}
}
