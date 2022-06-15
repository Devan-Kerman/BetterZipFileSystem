package net.devtech.betterzipfs.impl;

import java.nio.file.FileSystem;

public class ZipFSInternal {
	public static ZipFS wrap(FileSystem system) {
		if(system instanceof ZipFS z) {
			return z;
		}
		ZipFSReflect.ZIPFS.cast(system);
		ZipFSProvider instance = ZipFSProvider.getProvider();
		return ZipFSProvider.FILE_SYSTEMS.computeIfAbsent(system, instance.wrap);
	}
}
