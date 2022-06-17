package net.devtech.betterzipfs.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

// todo implement newOutputStream/newInputStream/newFileChannel
public class ZipFSProvider extends FileSystemProvider {
	static final Map<FileSystem, BetterZipFS> FILE_SYSTEMS = new HashMap<>();
	public static boolean overrideDefaultImplementation = System.getProperty("bzfs", "override").equals("override");
	private static ZipFSProvider instance;
	
	final Function<FileSystem, BetterZipFS> wrap = fs -> new BetterZipFS(fs, this);
	
	public ZipFSProvider() {
		instance = this;
	}
	
	public static ZipFSProvider getProvider() {
		if(instance == null) {
			return new ZipFSProvider();
		} else {
			return instance;
		}
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
		ZipPath zip = zip(path, true);
		try {
			return zip.getOrCreateContents(() -> ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newByteChannel(zip.delegate, options, attrs), write);
		} catch(Exception e) {
			throw ZipFSReflect.rethrow(e);
		}
	}
	
	public static Stream<Path> chaoticStream(FileSystem system) throws IOException {
		if(system instanceof BetterZipFS z) {
			z.flush(null);
			return ZipFSProvider.unorderedOptimizedStream(z.zipfs).map(path -> new ZipPath(z, path));
		} else if(ZipFSReflect.ZIPFS.isInstance(system)) {
			return ZipFSProvider.unorderedOptimizedStream(system);
		} else {
			return walk(system);
		}
	}
	
	public static Stream<Path> walk(FileSystem system) throws IOException { // binary merge
		List<Stream<Path>> streams = new ArrayList<>();
		for(Path directory : system.getRootDirectories()) {
			streams.add(Files.walk(directory));
		}
		
		while(streams.size() > 1) {
			for(int i = streams.size() - 1; i > 0; i -= 2) {
				Stream<Path> a = streams.remove(i);
				Stream<Path> b = streams.remove(i - 1);
				streams.add(Stream.concat(a, b));
			}
		}
		
		return streams.get(0);
	}
	
	public static void flush(Path path) throws IOException {
		zip(path, true).flushContents();
	}
	
	public static Stream<Path> unorderedOptimizedStream(FileSystem zipfs) {
		Map<?, ?> inodes = ZipFSReflect.ZipFS.getInodes(zipfs);
		return inodes.values().stream().map(ZipFSReflect.IndexNode::getName).map(n -> ZipFSReflect.ZipPath.fromName(zipfs, n, true));
	}
	
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
		BetterZipFS system = zip(dir, false).getFileSystem();
		system.flush(dir);
		return new DirectoryStream<>() {
			final DirectoryStream<Path> original = ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newDirectoryStream(unwrap(dir, false), filter);
			
			@Override
			public Iterator<Path> iterator() {
				return new MappingIterable.Iterator<>(this.original.iterator(), p -> ((BetterZipFS) dir.getFileSystem()).wrap(p));
			}
			
			@Override
			public void close() throws IOException {
				this.original.close();
			}
		};
	}
	
	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.createDirectory(unwrap(dir, false), attrs);
	}
	
	@Override
	public void delete(Path path) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.delete(unwrap(path, false));
		ZipPath zip = zip(path, true);
		zip.deleteContents(new ZipPath.ZipContents());
		zip.getFileSystem().remove(zip);
	}
	
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		ZipPath from = zip(source, true), to = zip(target, true);
		Path fromD = from.delegate;
		Path toD = to.delegate;
		
		to.inheritContents(from);
		from.flushContents(); // write to zip file to compress
		
		FileSystem fromS = fromD.getFileSystem(), toS = toD.getFileSystem();
		boolean fallback = true;
		try {
			byte[] fromPath = ZipFSReflect.ZipPath.getResolvedPath(fromD), toPath = ZipFSReflect.ZipPath.getResolvedPath(toD);
			BasicFileAttributes fromEntry = ZipFSReflect.ZipFS.getEntry(fromS, fromPath), toEntry = ZipFSReflect.ZipFS.getEntry(toS, toPath);
			ZipFSReflect.ZipFS.beginWrite(toS);
			ZipFSReflect.ZipFS.beginWrite(fromS);
			int method = ZipFSReflect.Entry.getCompressionMethod(fromEntry);
			int type = ZipFSReflect.Entry.getType(fromEntry);
			if(type == 1 || type == 4) {
				InputStream stream = ZipFSReflect.Entry.getCENInputStream(fromS, fromEntry);
				ZipFSReflect.Entry.setType(fromEntry, 2);
				ZipFSReflect.Entry.setBytes(fromEntry, stream.readAllBytes());
				type = 2;
			}
			
			if(toEntry == null) {
				ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.newOutputStream(toD).close();
				ZipFSReflect.ZipFS.endWrite(toS);
				ZipFSReflect.ZipFS.endWrite(fromS);
				toEntry = ZipFSReflect.ZipFS.getEntry(toS, toPath);
				ZipFSReflect.ZipFS.beginWrite(toS);
				ZipFSReflect.ZipFS.beginWrite(fromS);
			}
			
			if(toEntry != null && type == 2 && method == ZipFSReflect.Entry.getCompressionMethod(toEntry)) {
				ZipFSReflect.Entry.setBytes(toEntry, ZipFSReflect.Entry.getBytes(fromEntry));
				ZipFSReflect.Entry.setExtraBytes(toEntry, ZipFSReflect.Entry.getExtraBytes(fromEntry));
				ZipFSReflect.Entry.setCRC(toEntry, ZipFSReflect.Entry.getCRC(fromEntry));
				ZipFSReflect.Entry.setSize(toEntry, ZipFSReflect.Entry.getSize(fromEntry));
				ZipFSReflect.Entry.setCSize(toEntry, ZipFSReflect.Entry.getCSize(fromEntry));
				ZipFSReflect.ZipFS.endWrite(toS);
				ZipFSReflect.ZipFS.endWrite(fromS);
				ZipFSReflect.ZipFS.update(toS, toEntry);
				fallback = false;
			}
		} finally {
			if(fallback) {
				ZipFSReflect.ZipFS.endWrite(toS);
				ZipFSReflect.ZipFS.endWrite(fromS);
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
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.isSameFile(unwrap(path, false), unwrap(path2, false));
	}
	
	@Override
	public boolean isHidden(Path path) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.isHidden(unwrap(path, false));
	}
	
	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return new ZipFileStore(ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.getFileStore(unwrap(path, false)));
	}
	
	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.checkAccess(unwrap(path, false), modes);
	}
	
	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.getFileAttributeView(unwrap(path, false), type, options);
	}
	
	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.readAttributes(unwrap(path, false), type, options);
	}
	
	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.readAttributes(unwrap(path, false), attributes, options);
	}
	
	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		ZipFileSystemProviderHolder.ZIP_FS_PROVIDER.setAttribute(path, attribute, value, options);
	}
	
	static ZipPath zip(Path path, boolean unmirror) {
		ZipPath path1 = (ZipPath) path;
		return unmirror ? path1.unmirror() : path1;
	}
	
	static Path unwrap(Path path, boolean unmirror) {
		return path instanceof ZipPath z ? (unmirror ? z.unmirror() : z).delegate : path;
	}
}
