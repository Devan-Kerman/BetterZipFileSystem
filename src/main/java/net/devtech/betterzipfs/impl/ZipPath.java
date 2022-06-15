package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Callable;

class ZipPath implements Path {
	private static final VarHandle ZIP_CONTENTS_REF_COUNTER;
	static {
		try {
			ZIP_CONTENTS_REF_COUNTER = MethodHandles.privateLookupIn(ZipContents.class, MethodHandles.lookup()).findVarHandle(ZipContents.class, "ref", int.class);
		} catch(NoSuchFieldException | IllegalAccessException e) {
			throw ZipFSReflect.rethrow(e);
		}
	}
	static final class ZipContents {
		int ref;
		SeekableByteChannel channel;
		boolean isWrite;
	}
	
	ZipContents contents;
	final ZipFS fs;
	final Path delegate;
	Path parent;
	
	volatile String str;
	
	public ZipPath(ZipFS fs, Path delegate) {
		this.fs = fs;
		this.delegate = delegate;
		ZipContents contents = new ZipContents();
		ZIP_CONTENTS_REF_COUNTER.setVolatile(contents, 1);
		this.contents = contents;
	}
	
	public SeekableByteChannel getOrCreateContents(Callable<SeekableByteChannel> create, boolean isWrite) throws Exception {
		SeekableByteChannel seek;
		if((this.contents.isWrite || !isWrite) && this.contents.channel != null) {
			seek = this.contents.channel;
		} else {
			seek = create.call();
		}
		return new SeekableByteChannelWrapper(seek);
	}
	
	public void deleteContents(ZipContents newContents) throws IOException {
		this.flushContents();
		this.contents = newContents;
	}
	
	public void flushContents() throws IOException {
		ZipContents contents = this.contents;
		boolean copy = (int)ZIP_CONTENTS_REF_COUNTER.getAndAdd(contents, -1) > 1;
		SeekableByteChannel channel = contents.channel;
		if(copy) {
			contents.channel = new SeekableByteChannelCopy(channel);
		} else {
			contents.channel = null;
		}
		contents.isWrite = false;
		
		if(channel != null && !(channel instanceof SeekableByteChannelCopy)) {
			channel.close();
		}
	}
	
	public void inheritContents(ZipPath path) throws IOException {
		this.flushContents();
		if((int)ZIP_CONTENTS_REF_COUNTER.getAndAdd(path.contents, 1) > 0) {
			this.contents = path.contents;
		}
	}
	
	@Override
	public ZipFS getFileSystem() {
		return this.fs;
	}
	
	@Override
	public boolean isAbsolute() {
		return this.delegate.isAbsolute();
	}
	
	protected Path wrap(Path zipPath) {
		if(zipPath == this.delegate) {
			return this;
		}
		return this.fs.wrap(zipPath);
	}
	
	@Override
	public Path getRoot() {
		return this.fs.getRoot();
	}
	
	@Override
	public Path getFileName() {
		return wrap(this.delegate.getFileName());
	}
	
	@Override
	public Path getParent() {
		Path parent = this.parent;
		if(parent == null) {
			this.parent = parent = wrap(this.delegate.getParent());
		}
		return parent;
	}
	
	@Override
	public int getNameCount() {
		return this.delegate.getNameCount();
	}
	
	@Override
	public Path getName(int index) { // maybe return this if filename?
		return wrap(this.delegate.getName(index));
	}
	
	@Override
	public Path subpath(int beginIndex, int endIndex) {
		return wrap(this.delegate.subpath(beginIndex, endIndex));
	}
	
	@Override
	public boolean startsWith(Path other) {
		if(other instanceof ZipPath p) other = p.delegate;
		return this.delegate.startsWith(other);
	}
	
	@Override
	public boolean endsWith(Path other) {
		if(other instanceof ZipPath p) other = p.delegate;
		return this.delegate.endsWith(other);
	}
	
	@Override
	public Path normalize() {
		return wrap(this.delegate.normalize());
	}
	
	@Override
	public Path resolve(Path other) {
		if(other instanceof ZipPath p) other = p.delegate;
		return wrap(this.delegate.resolve(other));
	}
	
	@Override
	public Path relativize(Path other) {
		if(other instanceof ZipPath p) other = p.delegate;
		return wrap(this.delegate.resolve(other));
	}
	
	@Override
	public URI toUri() {
		return this.delegate.toUri();
	}
	
	@Override
	public Path toAbsolutePath() {
		return wrap(this.delegate.toAbsolutePath());
	}
	
	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		return wrap(this.delegate.toRealPath(options));
	}
	
	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
		return this.delegate.register(watcher, events, modifiers);
	}
	
	@Override
	public int compareTo(Path other) {
		if(other instanceof ZipPath p) other = p.delegate;
		return this.delegate.compareTo(other);
	}
	
	@Override
	public String toString() {
		String str = this.str;
		if(str != null) {
			this.str = str = this.delegate.toString();
		}
		return str;
	}
}
