package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// todo implement newOutputStream/newInputStream/newFileChannel
public class ZipFSProvider extends FileSystemProvider {
	public static boolean overrideDefaultImplementation = System.getProperty("bzfs","override").equals("override");
	
	static final Map<FileSystem, ZipFS> FILE_SYSTEMS = new HashMap<>();
	private static ZipFSProvider instance;
	
	final Function<FileSystem, ZipFS> wrap = fs -> new ZipFS(fs, this);
	
	public static ZipFSProvider getProvider() {
		if(instance == null) {
			return new ZipFSProvider();
		} else {
			return instance;
		}
	}
	
	public ZipFSProvider() {
		instance = this;
	}
	
	@Override
	public String getScheme() {
		return overrideDefaultImplementation ? "jar" : "zip";
	}
	
	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		FileSystem system = ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newFileSystem(uri, env);
		return FILE_SYSTEMS.computeIfAbsent(system, this.wrap);
	}
	
	@Override
	public FileSystem getFileSystem(URI uri) {
		FileSystem system = ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.getFileSystem(uri);
		return FILE_SYSTEMS.computeIfAbsent(system, this.wrap);
	}
	
	@Override
	public Path getPath(URI uri) {
		String spec = uri.getSchemeSpecificPart();
		int sep = spec.indexOf("!/");
		if(sep == -1) {
			throw new IllegalArgumentException("URI: " + uri + " does not contain path info ex. jar:file:/c:/foo.zip!/BAR");
		}
		return this.getFileSystem(uri).getPath(spec.substring(sep + 1));
	}
	
	@Override
	public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		FileSystem system = ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newFileSystem(path, env);
		return FILE_SYSTEMS.computeIfAbsent(system, this.wrap);
	}
	
	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		boolean write = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND);
		ZipPath zip = zip(path);
		try {
			return zip.getOrCreateContents(() -> ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newByteChannel(zip.delegate, options, attrs), write);
		} catch(Exception e) {
			throw ZipFSReflect.rethrow(e);
		}
	}
	
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
		return new DirectoryStream<>() {
			final DirectoryStream<Path> original = ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newDirectoryStream(unwrap(dir), filter);
			
			@Override
			public Iterator<Path> iterator() {
				return new MappingIterable.Iterator<>(this.original.iterator(), p -> ((ZipFS) dir.getFileSystem()).wrap(p));
			}
			
			@Override
			public void close() throws IOException {
				this.original.close();
			}
		};
	}
	
	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.createDirectory(unwrap(dir), attrs);
	}
	
	@Override
	public void delete(Path path) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.delete(unwrap(path));
		ZipPath zip = zip(path);
		zip.deleteContents(new ZipPath.ZipContents());
		zip.getFileSystem().remove(zip);
	}
	
	private static final Set<OpenOption> WRITE_ARGS = Set.of(StandardOpenOption.WRITE);
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		ZipPath from = zip(source), to = zip(target);
		Path fromD = from.delegate;
		Path toD = to.delegate;
		
		to.inheritContents(from);
		from.flushContents(); // write to zip file to compress
		
		FileSystem fromS = fromD.getFileSystem(), toS = toD.getFileSystem();
		boolean fallback = true;
		if(!Files.isSameFile(ZipFSReflect.ZipFS.getZipFile(fromS), ZipFSReflect.ZipFS.getZipFile(toS))) {
			byte[] fromPath = ZipFSReflect.ZipPath.getResolvedPath(fromD), toPath = ZipFSReflect.ZipPath.getResolvedPath(toD);
			BasicFileAttributes fromEntry = ZipFSReflect.ZipFS.getEntry(fromS, fromPath), toEntry = ZipFSReflect.ZipFS.getEntry(toS, toPath);
			
			int method = ZipFSReflect.Entry.getCompressionMethod(fromEntry);
			int type = ZipFSReflect.Entry.getType(fromEntry);
			if(type == 1) {
				// read from CEN
				SeekableByteChannel readChannel = this.newByteChannel(source, Set.of());
				ByteBuffer contents = new SeekableByteChannelCopy(readChannel).contents;
				try(SeekableByteChannel channel = this.newByteChannel(source, WRITE_ARGS)) {
					channel.write(contents);
				}
				to.inheritContents(from);
				from.flushContents();
				fromEntry = ZipFSReflect.ZipFS.getEntry(fromS, fromPath);
				type = ZipFSReflect.Entry.getType(fromEntry);
			}
			
			if(toEntry == null) {
				ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newOutputStream(toD).close();
				toEntry = ZipFSReflect.ZipFS.getEntry(toS, toPath);
			}
			
			if(type == 2 && method == ZipFSReflect.Entry.getCompressionMethod(toEntry)) {
				ZipFSReflect.Entry.setBytes(toEntry, ZipFSReflect.Entry.getBytes(fromEntry));
				ZipFSReflect.Entry.setExtraBytes(toEntry, ZipFSReflect.Entry.getExtraBytes(fromEntry));
				ZipFSReflect.Entry.setCRC(toEntry, ZipFSReflect.Entry.getCRC(fromEntry));
				ZipFSReflect.Entry.setSize(toEntry, ZipFSReflect.Entry.getSize(fromEntry));
				ZipFSReflect.Entry.setCSize(toEntry, ZipFSReflect.Entry.getCSize(fromEntry));
				ZipFSReflect.ZipFS.update(toS, toEntry);
				fallback = false;
			}
		}
		
		if(fallback) {
			try {
				ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.copy(fromD, toD, options);
			} catch(IOException e) {
				to.deleteContents(new ZipPath.ZipContents());
				throw e;
			}
		}
	}
	
	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		this.copy(source, target, options);
		Files.delete(source);
	}
	
	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.isSameFile(unwrap(path), unwrap(path2));
	}
	
	@Override
	public boolean isHidden(Path path) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.isHidden(unwrap(path));
	}
	
	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return new ZipFileStore(ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.getFileStore(unwrap(path)));
	}
	
	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.checkAccess(unwrap(path), modes);
	}
	
	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.getFileAttributeView(unwrap(path), type, options);
	}
	
	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.readAttributes(unwrap(path), type, options);
	}
	
	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.readAttributes(unwrap(path), attributes, options);
	}
	
	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.setAttribute(path, attribute, value, options);
	}
	
	static ZipPath zip(Path path) {
		return ((ZipPath) path).unmirror();
	}
	
	static Path unwrap(Path path) {
		return path instanceof ZipPath z ? z.unmirror().delegate : path;
	}
}
