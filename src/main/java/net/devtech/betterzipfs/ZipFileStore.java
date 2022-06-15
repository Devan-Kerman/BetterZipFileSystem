package net.devtech.betterzipfs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

public class ZipFileStore extends FileStore {
	final FileStore delegate;
	
	public ZipFileStore(FileStore delegate) {this.delegate = delegate;}
	
	@Override
	public String name() {
		return delegate.name();
	}
	
	@Override
	public String type() {
		return delegate.type();
	}
	
	@Override
	public boolean isReadOnly() {
		return delegate.isReadOnly();
	}
	
	@Override
	public long getTotalSpace() throws IOException {
		return delegate.getTotalSpace();
	}
	
	@Override
	public long getUsableSpace() throws IOException {
		return delegate.getUsableSpace();
	}
	
	@Override
	public long getUnallocatedSpace() throws IOException {
		return delegate.getUnallocatedSpace();
	}
	
	@Override
	public long getBlockSize() throws IOException {
		return delegate.getBlockSize();
	}
	
	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return delegate.supportsFileAttributeView(type);
	}
	
	@Override
	public boolean supportsFileAttributeView(String name) {
		return delegate.supportsFileAttributeView(name);
	}
	
	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		return delegate.getFileStoreAttributeView(type);
	}
	
	@Override
	public Object getAttribute(String attribute) throws IOException {
		return delegate.getAttribute(attribute);
	}
}
