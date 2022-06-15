package net.devtech.betterzipfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class SeekableByteChannelWrapper implements SeekableByteChannel {
	final SeekableByteChannel channel;
	final AtomicInteger refCounter;
	boolean isClosed;
	long pos, size = -1;
	
	public SeekableByteChannelWrapper(SeekableByteChannel channel, AtomicInteger counter) {
		this.channel = channel;
		this.refCounter = counter;
		if(counter != null) {
			counter.incrementAndGet();
		}
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
			if(this.refCounter == null || this.refCounter.decrementAndGet() <= 0) {
				this.channel.close();
			}
		}
	}
}
