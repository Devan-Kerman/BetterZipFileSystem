package net.devtech.betterzipfs.impl;

import java.nio.file.spi.FileSystemProvider;

class ZipFileSystemProviderHolder {
	private static final Class<?> ZIP_FS_PROVIDER;
	public static final FileSystemProvider PROVIDER;
	
	static {
		try {
			ZIP_FS_PROVIDER = Class.forName("jdk.nio.zipfs.ZipFileSystemProvider");
			
			FileSystemProvider zipfsProvider = null;
			for(FileSystemProvider provider : FileSystemProvider.installedProviders()) {
				if(ZIP_FS_PROVIDER.isInstance(provider)) {
					zipfsProvider = provider;
				}
			}
			PROVIDER = zipfsProvider;
		} catch(ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
