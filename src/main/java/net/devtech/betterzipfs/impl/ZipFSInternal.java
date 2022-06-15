package net.devtech.betterzipfs.impl;

import java.nio.file.FileSystem;

public class ZipFSInternal {
	public static ZipFS wrap(FileSystem system) {
		ZipFSReflect.ZIPFS.cast(system);
		return ZipFSProvider.INSTANCE.filesystems.computeIfAbsent(system, ZipFS::new);
	}
}
