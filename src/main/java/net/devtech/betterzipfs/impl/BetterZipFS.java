package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public class BetterZipFS extends FileSystem {
	public final FileSystem zipfs;
	public final Map<ByteArrayWrapper, ZipPath> pathCache = new ConcurrentHashMap<>();
	final UnaryOperator<Path> converter = this::wrap;
	final Path root;
	final ZipFSProvider provider;
	
	public BetterZipFS(FileSystem zipfs, ZipFSProvider provider) {
		this.zipfs = zipfs;
		this.provider = provider;
		Iterable<Path> directories = zipfs.getRootDirectories();
		Iterator<Path> iterator = directories.iterator();
		this.root = this.wrap(iterator.next());
		if(iterator.hasNext()) {
			throw new UnsupportedOperationException("Multiple Roots of " + zipfs);
		}
	}
	
	public Path getRoot() {
		return this.root;
	}
	
	public void remove(ZipPath zipFile) {
		byte[] path = ZipFSReflect.ZipPath.getResolvedPath(zipFile.delegate);
		this.pathCache.remove(new ByteArrayWrapper(path));
	}
	
	public Path wrap(Path zipFile) {
		return new ZipPath(this, zipFile);
	}
	//[47, 116, 101, 115, 116, 46, 116, 120, 116]
	public ZipPath wrapCached(Path zipFile, ZipPath alternative) {
		byte[] path = ZipFSReflect.ZipPath.getResolvedPath(zipFile);
		ByteArrayWrapper wrapper = new ByteArrayWrapper(path);
		ZipPath paths = this.pathCache.putIfAbsent(wrapper, alternative);
		return paths == null ? alternative : paths;
	}
	
	@Override
	public FileSystemProvider provider() {
		return this.provider;
	}
	
	@Override
	public void close() throws IOException {
		for(ZipPath value : this.pathCache.values()) {
			value.deleteContents(null);
		}
		this.zipfs.close();
		ZipFSProvider.FILE_SYSTEMS.remove(this.zipfs, this);
	}
	
	@Override
	public boolean isOpen() {
		return this.zipfs.isOpen();
	}
	
	@Override
	public boolean isReadOnly() {
		return this.zipfs.isReadOnly();
	}
	
	@Override
	public String getSeparator() {
		return this.zipfs.getSeparator();
	}
	
	@Override
	public Iterable<Path> getRootDirectories() {
		return new MappingIterable<>(this.zipfs.getRootDirectories(), this.converter);
	}
	
	@Override
	public Iterable<FileStore> getFileStores() {
		return new MappingIterable<>(this.zipfs.getFileStores(), ZipFileStore::new);
	}
	
	@Override
	public Set<String> supportedFileAttributeViews() {
		return this.zipfs.supportedFileAttributeViews();
	}
	
	@Override
	public Path getPath(String first, String... more) {
		return this.wrap(this.zipfs.getPath(first, more));
	}
	
	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		PathMatcher matcher = this.zipfs.getPathMatcher(syntaxAndPattern);
		return p -> matcher.matches(p instanceof ZipPath z ? z.delegate : p);
	}
	
	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public WatchService newWatchService() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		return this.zipfs.toString();
	}
	
	public void flush() throws IOException {
		for(ZipPath value : this.pathCache.values()) {
			value.flushContents();
		}
	}
}
