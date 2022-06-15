package net.devtech.betterzipfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

import net.devtech.betterzipfs.impl.ZipFSInternal;

public final class ZipFS {
	
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
}
