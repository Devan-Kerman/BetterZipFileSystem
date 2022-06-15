package net.devtech.betterzipfs;

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

import net.devtech.betterzipfs.reflect.ZipFSReflect;
import net.devtech.betterzipfs.util.ByteArrayWrapper;
import net.devtech.betterzipfs.util.MappingIterable;

public class ZipFS extends FileSystem {
	final FileSystem zipfs;
	final Map<ByteArrayWrapper, ZipPath> pathCache = new ConcurrentHashMap<>();
	final UnaryOperator<Path> converter = this::wrap;
	final Path root;
	
	public ZipFS(FileSystem zipfs) {
		this.zipfs = zipfs;
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
		byte[] path = ZipFSReflect.ZipPath.getResolvedPath(zipFile);
		ByteArrayWrapper wrapper = new ByteArrayWrapper(path);
		return this.pathCache.computeIfAbsent(wrapper, name -> new ZipPath(this, zipFile));
	}
	
	public Path wrapRef(ZipPath original, Path wrap) {
		byte[] path = ZipFSReflect.ZipPath.getResolvedPath(wrap);
		ByteArrayWrapper wrapper = new ByteArrayWrapper(path);
		return this.pathCache.computeIfAbsent(wrapper, name -> new ZipPath(this, wrap, original.parent, original.reference));
	}
	
	@Override
	public FileSystemProvider provider() {
		return ZipFSProvider.INSTANCE;
	}
	
	@Override
	public void close() throws IOException {
		for(ZipPath value : this.pathCache.values()) {
			if(value.reference.channel != null) {
				value.reference.channel.close();
			}
		}
		this.zipfs.close();
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
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		return this.zipfs.toString();
	}
}
