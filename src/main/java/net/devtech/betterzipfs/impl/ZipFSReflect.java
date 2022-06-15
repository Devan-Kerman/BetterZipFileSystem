package net.devtech.betterzipfs.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class ZipFSReflect {
	public static final Class<?> ZIPFS;
	private static final MethodHandle ZIPFS_SYNC, ZIPFS_ENTRY, ZIPPATH_RESOLVED_PATH, ZIPFS_GETZIPFILE, ZIPFS_UPDATE, BEGIN_WRITE, END_WRITE;
	private static final VarHandle ENTRY_METHOD, ENTRY_BYTES, ZIPFS_HAS_UPDATE, ENTRY_CRC, ENTRY_CSIZE, ENTRY_SIZE, ENTRY_EXTRA;
	static {
		boolean needsUnsafe = false;
		try {
			MethodHandles.privateLookupIn(Class.forName("jdk.nio.zipfs.ZipFileSystem"), MethodHandles.lookup());
		} catch(ReflectiveOperationException e) {
			needsUnsafe = true;
		}
		
		boolean canUseUnsafe = false;
		try {
			Class.forName("sun.misc.Unsafe");
			canUseUnsafe = UnsafeReflection.IS_SUPPORTED;
		} catch(ClassNotFoundException e) {
		}
		
		if(!canUseUnsafe && needsUnsafe) {
			throw new IllegalStateException("Unsafe is required for reflection access bypass! Alternatively, try adding \"--add-opens=jdk.zipfs/jdk.nio.zipfs=ALL-UNNAMED\" to jvm args.");
		}
		
		
		Module module = ZipFSReflect.class.getModule();
		try {
			Class<?> zipfs = Class.forName("jdk.nio.zipfs.ZipFileSystem");
			Class<?> zipPath = Class.forName("jdk.nio.zipfs.ZipPath");
			if(needsUnsafe) {
				UnsafeReflection.startUnsafe(ZipFSReflect.class, zipfs);
			}
			
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(zipfs, lookup);
			Class<?> entry = null;
			for(Class<?> inner : zipfs.getDeclaredClasses()) {
				if(inner.getName().endsWith("Entry")) {
					entry = inner;
					break;
				}
			}
			
			ZIPFS_SYNC = privateLookup.findVirtual(zipfs, "sync", MethodType.methodType(void.class));
			ZIPFS_ENTRY = privateLookup.findVirtual(zipfs, "getEntry", MethodType.methodType(entry, byte[].class));
			ZIPFS_GETZIPFILE = privateLookup.findVirtual(zipfs, "getZipFile", MethodType.methodType(Path.class));
			ZIPFS_UPDATE = privateLookup.findVirtual(zipfs, "update", MethodType.methodType(void.class, entry));
			ENTRY_METHOD = privateLookup.findVarHandle(entry, "method", int.class);
			ENTRY_BYTES = privateLookup.findVarHandle(entry, "bytes", byte[].class);
			ENTRY_CRC = privateLookup.findVarHandle(entry, "crc", long.class);
			ENTRY_CSIZE = privateLookup.findVarHandle(entry, "csize", long.class);
			ENTRY_SIZE = privateLookup.findVarHandle(entry, "size", long.class);
			ZIPPATH_RESOLVED_PATH = privateLookup.findVirtual(zipPath, "getResolvedPath", MethodType.methodType(byte[].class));
			ZIPFS_HAS_UPDATE = privateLookup.findVarHandle(zipfs, "hasUpdate", boolean.class);
			BEGIN_WRITE = privateLookup.findVirtual(zipfs, "beginWrite", MethodType.methodType(void.class));
			END_WRITE = privateLookup.findVirtual(zipfs, "endWrite", MethodType.methodType(void.class));
			ENTRY_EXTRA = privateLookup.findVarHandle(entry, "extra", byte[].class);
			ZIPFS = zipfs;
		} catch(ReflectiveOperationException e) {
			if(!needsUnsafe) {
				new UnsupportedOperationException("Unsafe Reflection Restriction Bypass is Unsupported on this machine!", UnsafeReflection.ERROR).printStackTrace();
			}
			throw new UnsupportedOperationException("Try adding \"--add-opens=jdk.zipfs/jdk.nio.zipfs=ALL-UNNAMED\" to jvm args.", e);
		} finally {
			if(needsUnsafe) {
				UnsafeReflection.endUnsafe(ZipFSReflect.class, module);
			}
		}
	}
	
	public static final class ZipFS {
		public static void sync(FileSystem system) {
			try {
				BEGIN_WRITE.invoke(system);
				ZIPFS_SYNC.invoke(system);
			} catch(Throwable e) {
				rethrow(e);
			} finally {
				try {
					END_WRITE.invoke(system);
				} catch(Throwable e) {
					rethrow(e);
				}
			}
		}
		
		public static BasicFileAttributes getEntry(FileSystem system, byte[] name) {
			try {
				return (BasicFileAttributes) ZIPFS_ENTRY.invoke(system, name);
			} catch(Throwable e) {
				throw rethrow(e);
			}
		}
		
		public static Path getZipFile(FileSystem system) {
			try {
				return (Path) ZIPFS_GETZIPFILE.invoke(system);
			} catch(Throwable e) {
				throw rethrow(e);
			}
		}
		
		public static void hasUpdate(FileSystem system) {
			ZIPFS_HAS_UPDATE.set(system, true);
		}
		
		public static void update(FileSystem system, BasicFileAttributes entry) {
			try {
				ZIPFS_UPDATE.invoke(system, entry);
			} catch(Throwable e) {
				rethrow(e);
			}
		}
	}
	
	public static final class Entry {
		public static int getCompressionMethod(BasicFileAttributes entry) {
			return (int) ENTRY_METHOD.get(entry);
		}
		
		public static byte[] getBytes(BasicFileAttributes entry) {
			return (byte[]) ENTRY_BYTES.get(entry);
		}
		
		public static void setBytes(BasicFileAttributes entry, byte[] bytes) {
			ENTRY_BYTES.set(entry, bytes);
		}
		
		public static byte[] getExtraBytes(BasicFileAttributes entry) {
			return (byte[]) ENTRY_EXTRA.get(entry);
		}
		
		public static void setExtraBytes(BasicFileAttributes entry, byte[] bytes) {
			ENTRY_EXTRA.set(entry, bytes);
		}
		
		public static long getCRC(BasicFileAttributes entry) {
			return (long) ENTRY_CRC.get(entry);
		}
		
		public static void setCRC(BasicFileAttributes entry, long bytes) {
			ENTRY_CRC.set(entry, bytes);
		}
		
		public static long getSize(BasicFileAttributes entry) {
			return (long) ENTRY_SIZE.get(entry);
		}
		
		public static void setSize(BasicFileAttributes entry, long bytes) {
			ENTRY_SIZE.set(entry, bytes);
		}
		
		public static long getCSize(BasicFileAttributes entry) {
			return (long) ENTRY_CSIZE.get(entry);
		}
		
		public static void setCSize(BasicFileAttributes entry, long bytes) {
			ENTRY_CSIZE.set(entry, bytes);
		}
	}
	
	public static final class ZipPath {
		public static byte[] getResolvedPath(Path path) {
			try {
				return (byte[]) ZIPPATH_RESOLVED_PATH.invoke(path);
			} catch(Throwable e) {
				throw rethrow(e);
			}
		}
	}
	
	/**
	 * @return nothing, because it throws
	 * @throws T rethrows {@code throwable}
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Throwable> RuntimeException rethrow(Throwable throwable) throws T {
		throw (T) throwable;
	}
}
