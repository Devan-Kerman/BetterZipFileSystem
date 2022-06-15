package net.devtech.betterzipfs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import net.devtech.betterzipfs.reflect.ZipFSReflect;
import net.devtech.betterzipfs.util.MappingIterable;

public class ZipFSProvider extends FileSystemProvider {
	public static final ZipFSProvider INSTANCE = new ZipFSProvider();
	private final Map<Path, ZipFS> filesystems = new HashMap<>();
	
	public ZipFSProvider() {
	}
	
	@Override
	public String getScheme() {
		return "zip";
	}
	
	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		Path path = this.uriToPath(uri);
		synchronized(this.filesystems) {
			Path realPath = null;
			if(this.ensureFile(path)) {
				realPath = path.toRealPath();
				if(this.filesystems.containsKey(realPath)) {
					throw new FileSystemAlreadyExistsException();
				}
			}
			ZipFS zipfs = this.getZipFileSystem(path, env);
			if(realPath == null) {  // newly created
				realPath = path.toRealPath();
			}
			this.filesystems.put(realPath, zipfs);
			return zipfs;
		}
	}
	
	@Override
	public FileSystem getFileSystem(URI uri) {
		synchronized(this.filesystems) {
			ZipFS zipfs = null;
			try {
				zipfs = this.filesystems.get(this.uriToPath(uri).toRealPath());
			} catch(IOException x) {
				// ignore the ioe from toRealPath(), return FSNFE
			}
			if(zipfs == null) {
				throw new FileSystemNotFoundException();
			}
			return zipfs;
		}
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
		this.ensureFile(path);
		return this.getZipFileSystem(path, env);
	}
	
	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		boolean write = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND);
		ZipPath zip = zip(path);
		SeekableByteChannel channel;
		if((!write || zip.reference.isWrite) && zip.reference.channel != null) {
			channel = zip.reference.channel;
		} else {
			zip.reference.close(false);
			channel = zip.reference.channel = ZipFileSystemProviderHolder.PROVIDER.newByteChannel(zip.delegate, options, attrs);
			zip.reference.isWrite = write;
			zip.reference.refCounter.set(1);
		}
		
		return new SeekableByteChannelWrapper(channel, zip.reference.refCounter);
	}
	
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
		return new DirectoryStream<>() {
			final DirectoryStream<Path> original = ZipFileSystemProviderHolder.PROVIDER.newDirectoryStream(unwrap(dir), filter);
			
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
		ZipFileSystemProviderHolder.PROVIDER.createDirectory(unwrap(dir), attrs);
	}
	
	@Override
	public void delete(Path path) throws IOException {
		ZipFileSystemProviderHolder.PROVIDER.delete(unwrap(path));
		ZipPath zip = zip(path);
		if(zip.reference.channel != null) {
			SeekableByteChannel channel = new SeekableByteChannelCopy(zip.reference.channel);
			zip.reference.close(false);
			zip.reference.channel = channel;
		}
		zip.getFileSystem().remove(zip);
	}
	
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		ZipPath from = zip(source), to = zip(target);
		Path fromD = from.delegate;
		Path toD = to.delegate;
		if(from.reference.isWrite) {
			SeekableByteChannel channel = new SeekableByteChannelCopy(from.reference.channel);
			from.reference.close(true);
			from.reference.channel = channel;
		}
		
		FileSystem fromS = fromD.getFileSystem(), toS = toD.getFileSystem();
		boolean fallback = true;
		if(!Files.isSameFile(ZipFSReflect.ZipFS.getZipFile(fromS), ZipFSReflect.ZipFS.getZipFile(toS))) {
			byte[] fromPath = ZipFSReflect.ZipPath.getResolvedPath(fromD), toPath = ZipFSReflect.ZipPath.getResolvedPath(toD);
			BasicFileAttributes fromEntry = ZipFSReflect.ZipFS.getEntry(fromS, fromPath), toEntry = ZipFSReflect.ZipFS.getEntry(toS, toPath);
			if(toEntry == null) {
				ZipFileSystemProviderHolder.PROVIDER.newOutputStream(toD).close();
				toEntry = ZipFSReflect.ZipFS.getEntry(toS, toPath);
			}
			if(ZipFSReflect.Entry.getCompressionMethod(fromEntry) == ZipFSReflect.Entry.getCompressionMethod(toEntry)) {
				ZipFSReflect.Entry.setBytes(toEntry, ZipFSReflect.Entry.getBytes(fromEntry));
				ZipFSReflect.Entry.setCRC(toEntry, ZipFSReflect.Entry.getCRC(fromEntry));
				ZipFSReflect.Entry.setSize(toEntry, ZipFSReflect.Entry.getSize(fromEntry));
				ZipFSReflect.Entry.setCSize(toEntry, ZipFSReflect.Entry.getCSize(fromEntry));
				ZipFSReflect.ZipFS.update(toS, toEntry);
				fallback = false;
			}
		}
		
		if(fallback) {
			ZipFileSystemProviderHolder.PROVIDER.copy(fromD, toD, options);
		}
		to.reference = new ZipPath.DataReference(from.reference);
	}
	
	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		ZipPath from = zip(source), to = zip(target);
		ZipFileSystemProviderHolder.PROVIDER.move(from.delegate, to.delegate, options);
		to.reference = from.reference;
		from.reference = null;
	}
	
	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return ZipFileSystemProviderHolder.PROVIDER.isSameFile(unwrap(path), unwrap(path2));
	}
	
	@Override
	public boolean isHidden(Path path) throws IOException {
		return ZipFileSystemProviderHolder.PROVIDER.isHidden(unwrap(path));
	}
	
	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return new ZipFileStore(ZipFileSystemProviderHolder.PROVIDER.getFileStore(unwrap(path)));
	}
	
	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		ZipFileSystemProviderHolder.PROVIDER.checkAccess(unwrap(path), modes);
	}
	
	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return ZipFileSystemProviderHolder.PROVIDER.getFileAttributeView(unwrap(path), type, options);
	}
	
	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
		return ZipFileSystemProviderHolder.PROVIDER.readAttributes(unwrap(path), type, options);
	}
	
	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return ZipFileSystemProviderHolder.PROVIDER.readAttributes(unwrap(path), attributes, options);
	}
	
	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		ZipFileSystemProviderHolder.PROVIDER.setAttribute(path, attribute, value, options);
	}
	
	static ZipPath zip(Path path) {
		return (ZipPath) path;
	}
	
	static Path unwrap(Path path) {
		return path instanceof ZipPath z ? z.delegate : path;
	}
	
	protected Path uriToPath(URI uri) {
		String scheme = uri.getScheme();
		if((scheme == null) || !scheme.equalsIgnoreCase(this.getScheme())) {
			throw new IllegalArgumentException("URI scheme is not '" + this.getScheme() + "'");
		}
		try {
			// only support legacy JAR URL syntax  jar:{uri}!/{entry} for now
			String spec = uri.getRawSchemeSpecificPart();
			int sep = spec.indexOf("!/");
			if(sep != -1) {
				spec = spec.substring(0, sep);
			}
			return Paths.get(new URI(spec)).toAbsolutePath();
		} catch(URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}
	
	private boolean ensureFile(Path path) {
		try {
			BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
			if(!attrs.isRegularFile()) {
				throw new UnsupportedOperationException();
			}
			return true;
		} catch(IOException ioe) {
			return false;
		}
	}
	
	private ZipFS getZipFileSystem(Path path, Map<String, ?> env) throws IOException {
		try {
			return new ZipFS(ZipFileSystemProviderHolder.PROVIDER.newFileSystem(path, env));
		} catch(ZipException ze) {
			String pname = path.toString();
			if(pname.endsWith(".zip") || pname.endsWith(".jar")) {
				throw ze;
			}
			throw new UnsupportedOperationException();
		}
	}
}
