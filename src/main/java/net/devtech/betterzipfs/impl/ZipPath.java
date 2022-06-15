package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.atomic.AtomicInteger;

class ZipPath implements Path {
	static class DataReference {
		AtomicInteger refCounter = new AtomicInteger();
		SeekableByteChannel channel;
		boolean isWrite;
		
		public DataReference(DataReference ref) {
			this.refCounter = ref.refCounter;
			this.channel = ref.channel;
			this.isWrite = ref.isWrite;
		}
		
		public DataReference() {
		}
		
		public DataReference(SeekableByteChannel channel) {
			this.channel = channel;
		}
		
		void close(boolean force) throws IOException {
			if(force || (this.refCounter.decrementAndGet() <= 0 && this.channel != null)) {
				this.channel.close();
				this.channel = null;
				this.isWrite = false;
			}
			this.refCounter = new AtomicInteger();
		}
	}
	
	final ZipFS fs;
	final Path delegate;
	DataReference reference; // todo synchronize
	Path parent;
	
	volatile String str;
	
	public ZipPath(ZipFS fs, Path delegate) {
		this.fs = fs;
		this.delegate = delegate;
		this.reference = new DataReference();
	}
	
	public ZipPath(ZipFS fs, Path delegate, DataReference ref) {
		this.fs = fs;
		this.delegate = delegate;
		this.reference = ref;
	}
	
	public ZipPath(ZipFS fs, Path delegate, Path parent, DataReference ref) {
		this(fs, delegate, ref);
		this.parent = parent;
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
	
	protected Path wrapRef(Path zipPath) {
		if(zipPath == this.delegate) {
			return this;
		}
		return this.fs.wrapRef(this, zipPath);
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
		return wrapRef(this.delegate.toAbsolutePath());
	}
	
	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		return wrapRef(this.delegate.toRealPath(options));
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
