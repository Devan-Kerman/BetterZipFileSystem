package net.devtech.betterzipfs.impl;

import java.nio.file.FileSystem;

public class ZipFSInternal {
	public static ZipFS wrap(FileSystem system) {
		ZipFSReflect.ZIPFS.cast(system);
		ZipFSProvider instance = ZipFSProvider.getProvider();
		return ZipFSProvider.FILE_SYSTEMS.computeIfAbsent(system, instance.wrap);
	}
}
