package net.devtech.betterzipfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

import net.devtech.betterzipfs.impl.ZipFSInternal;
import net.devtech.betterzipfs.impl.ZipFSReflect;

public final class ZipFS {
	// todo maybe fix SeekableByteChannelWrapper implementation cus of the limiter, eh it's not needed apparently
	// todo reduce buffer copying?
	
	private ZipFS() {}
	public static final Map<String, String> CREATE_SETTINGS = Map.of("create", "true");
	/**
	 * @deprecated unsafe
	 */
	@Deprecated
	public static FileSystem wrap(FileSystem system) {
		return ZipFSInternal.wrap(system);
	}
	
	public static FileSystem createZip(Path path) throws IOException {
		return newFileSystem(path, CREATE_SETTINGS);
	}
	
	public static FileSystem newFileSystem(URI uri, Map<String,?> env)
			throws IOException {
		return wrap(FileSystems.newFileSystem(uri, env));
	}
	
	public static FileSystem newFileSystem(URI uri, Map<String,?> env, ClassLoader loader)
			throws IOException {
		return wrap(FileSystems.newFileSystem(uri, env, loader));
	}
	
	public static FileSystem newFileSystem(
			Path path, ClassLoader loader) throws IOException {
		return wrap(FileSystems.newFileSystem(path, loader));
	}
	
	public static FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		return wrap(FileSystems.newFileSystem(path, env));
	}
	
	public static FileSystem newFileSystem(
			Path path, Map<String, ?> env, ClassLoader loader) throws IOException {
		return wrap(FileSystems.newFileSystem(path, env, loader));
	}
	
	public static FileSystem newFileSystem(Path path) throws IOException {
		return wrap(FileSystems.newFileSystem(path));
	}
	
	public static FileSystem getFileSystem(URI uri) {
		return wrap(FileSystems.getFileSystem(uri));
	}
	
	/**
	 * write the ZipFileSystem to the disk without closing it
	 * @param cast whether to throw an exception or silently fail if the FileSystem is not a zip file system
	 */
	public static void flush(FileSystem fs, boolean cast) throws IOException {
		FileSystem zipfs;
		if(fs instanceof net.devtech.betterzipfs.impl.ZipFS z) {
			zipfs = z.zipfs;
			z.flush();
		} else if(ZipFSReflect.ZIPFS.isInstance(fs)) {
			zipfs = fs;
		} else if(cast) {
			throw new IllegalStateException(fs + " is not a ZipFileSystem/ZipFS");
		} else {
			return;
		}
		
		ZipFSReflect.ZipFS.sync(zipfs);
	}
}
