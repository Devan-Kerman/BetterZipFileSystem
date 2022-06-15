package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

class SeekableByteChannelCopy implements SeekableByteChannel {
	boolean isOpen = true;
	public final ByteBuffer contents;
	
	public SeekableByteChannelCopy(ByteBuffer buffer) {
		this.contents = buffer;
	}
	
	public SeekableByteChannelCopy(SeekableByteChannel channel) throws IOException {
		ByteBuffer contents = ByteBuffer.allocate(Math.toIntExact(channel.size()));
		int position = Math.toIntExact(channel.position());
		channel.position(0);
		channel.read(contents);
		contents.position(position);
		this.contents = contents;
	}
	
	@Override
	public int read(ByteBuffer dst) throws IOException {
		int toRead = Math.min(this.contents.remaining(), dst.remaining());
		int position = dst.position();
		dst.put(position, this.contents, this.contents.position(), toRead);
		dst.position(position + toRead);
		return toRead;
	}
	
	@Override
	public int write(ByteBuffer src) throws IOException {
		throw new UnsupportedEncodingException("Read only!");
	}
	
	@Override
	public long position() throws IOException {
		return this.contents.position();
	}
	
	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		this.contents.position(Math.toIntExact(newPosition));
		return this;
	}
	
	@Override
	public long size() throws IOException {
		return this.contents.limit();
	}
	
	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		this.contents.limit(Math.toIntExact(size));
		return this;
	}
	
	@Override
	public boolean isOpen() {
		return this.isOpen;
	}
	
	@Override
	public void close() throws IOException {
		this.isOpen = false;
	}
}
