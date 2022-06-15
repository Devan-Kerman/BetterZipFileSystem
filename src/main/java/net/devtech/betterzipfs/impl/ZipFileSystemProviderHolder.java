package net.devtech.betterzipfs.impl;

import java.nio.file.spi.FileSystemProvider;
import java.util.Objects;

/**
 * Lazy initialization
 */
class ZipFileSystemProviderHolder {
	public static final FileSystemProvider ZIP_FS_PROVIDER;
	private static final Class<?> ZIP_FS_PROVIDER_CLS;
	
	static {
		try {
			ZIP_FS_PROVIDER_CLS = Class.forName("jdk.nio.zipfs.ZipFileSystemProvider");
			FileSystemProvider zipfsProvider = null;
			boolean installed = false;
			for(FileSystemProvider provider : FileSystemProvider.installedProviders()) {
				if(ZIP_FS_PROVIDER_CLS.isInstance(provider)) {
					zipfsProvider = provider;
				}
				if(provider instanceof ZipFSProvider) {
					installed = true;
				}
			}
			
			if(zipfsProvider == null) {
				zipfsProvider = new ZipFSProvider();
			}
			
			if(!installed) {
				Objects.requireNonNull(zipfsProvider, ZipFSProvider.class + " was not loaded, and " + ZIP_FS_PROVIDER_CLS + " could not be found!");
			}
			ZIP_FS_PROVIDER = zipfsProvider;
		} catch(ClassNotFoundException e) {
			throw ZipFSReflect.rethrow(e);
		}
	}
}
