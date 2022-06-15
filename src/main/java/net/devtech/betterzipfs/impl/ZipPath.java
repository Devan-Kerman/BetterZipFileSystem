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
	private static final ZipPath NOT_MIRROR = new ZipPath(null, null);
	
	static final class ZipContents {
		volatile int ref;
		volatile SeekableByteChannel channel;
		volatile boolean isWrite;
	}
	
	ZipContents contents; // null if file is deleted or this is a mirror instance
	ZipPath mirror;
	final ZipFS fs;
	final Path delegate;
	Path parent;
	
	volatile String str;
	
	public ZipPath(ZipFS fs, Path delegate) {
		this.fs = fs;
		this.delegate = delegate;
		ZipContents contents = new ZipContents();
		contents.ref = 1;
		this.contents = contents;
	}
	
	public ZipPath unmirror() {
		if(this.mirror == NOT_MIRROR) {
			return this;
		} if(this.contents == null) {
			return this.mirror;
		} else {
			ZipPath paths = this.fs.wrapCached(this.delegate, this);
			if(paths == this) { // not mirror
				this.mirror = NOT_MIRROR;
				return this;
			} else {
				this.mirror = paths;
				this.contents = null;
				return paths;
			}
		}
	}
	
	public SeekableByteChannel getOrCreateContents(Callable<SeekableByteChannel> create, boolean isWrite) throws Exception {
		SeekableByteChannel seek;
		if((this.contents.isWrite || !isWrite) && this.contents.channel != null) {
			seek = this.contents.channel;
		} else {
			ZipContents contents = this.contents;
			if(isWrite) {
				this.flushContents();
				contents = new ZipContents();
				contents.ref = 1;
				this.contents = contents;
			}
			contents.isWrite = isWrite;
			contents.channel = seek = create.call();
		}
		return new SeekableByteChannelWrapper(seek);
	}
	
	public void deleteContents(ZipContents newContents) throws IOException {
		this.flushContents();
		this.contents = newContents;
	}
	
	public void flushContents() throws IOException {
		ZipContents contents = this.contents;
		boolean isReferenceStillUsed = (int)ZIP_CONTENTS_REF_COUNTER.getAndAdd(contents, -1) > 1;
		SeekableByteChannel channel = contents.channel;
		boolean shouldCopy = channel != null && !(channel instanceof SeekableByteChannelCopy);
		contents.isWrite = false;
		if(isReferenceStillUsed) {
			if(shouldCopy) {
				contents.channel = new SeekableByteChannelCopy(channel);
			}
		} else {
			contents.channel = null;
		}
		
		if(shouldCopy) {
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
		return this.wrap(this.delegate.getFileName());
	}
	
	@Override
	public Path getParent() {
		Path parent = this.parent;
		if(parent == null) {
			this.parent = parent = this.wrap(this.delegate.getParent());
		}
		return parent;
	}
	
	@Override
	public int getNameCount() {
		return this.delegate.getNameCount();
	}
	
	@Override
	public Path getName(int index) { // maybe return this if filename?
		return this.wrap(this.delegate.getName(index));
	}
	
	@Override
	public Path subpath(int beginIndex, int endIndex) {
		return this.wrap(this.delegate.subpath(beginIndex, endIndex));
	}
	
	@Override
	public boolean startsWith(Path other) {
		other = ZipFSProvider.unwrap(other);
		return this.delegate.startsWith(other);
	}
	
	@Override
	public boolean endsWith(Path other) {
		other = ZipFSProvider.unwrap(other);
		return this.delegate.endsWith(other);
	}
	
	@Override
	public Path normalize() {
		return this.wrap(this.delegate.normalize());
	}
	
	@Override
	public Path resolve(Path other) {
		other = ZipFSProvider.unwrap(other);
		return this.wrap(this.delegate.resolve(other));
	}
	
	@Override
	public Path relativize(Path other) {
		other = ZipFSProvider.unwrap(other);
		return this.wrap(this.delegate.resolve(other));
	}
	
	@Override
	public URI toUri() {
		return this.delegate.toUri();
	}
	
	@Override
	public Path toAbsolutePath() {
		return this.wrap(this.delegate.toAbsolutePath());
	}
	
	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		return this.wrap(this.delegate.toRealPath(options));
	}
	
	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
		return this.delegate.register(watcher, events, modifiers);
	}
	
	@Override
	public int compareTo(Path other) {
		other = ZipFSProvider.unwrap(other);
		return this.delegate.compareTo(other);
	}
	
	@Override
	public String toString() {
		String str = this.str;
		if(str == null) {
			this.str = str = this.delegate.toString();
		}
		return str;
	}
}
