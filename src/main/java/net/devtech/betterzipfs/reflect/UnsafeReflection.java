package net.devtech.betterzipfs.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import sun.misc.Unsafe;

public class UnsafeReflection {
	public static final boolean IS_SUPPORTED;
	public static final Object UNSAFE;
	public static final Throwable ERROR;
	private static final long MODULE_OFFSET;
	
	static {
		Throwable error = null;
		Object unsafeInstance = null;
		Field module = null;
		long moduleOffset = 0;
		try {
			// get unsafe
			Unsafe unsafe = null;
			for(Field field : Unsafe.class.getDeclaredFields()) {
				if(field.getType() == Unsafe.class && Modifier.isStatic(field.getModifiers())) {
					try {
						field.setAccessible(true);
						unsafe = (Unsafe) field.get(null);
						break;
					} catch(Exception e) {
					}
				}
			}
			
			unsafeInstance = unsafe;
			module = Class.class.getDeclaredField("module");
			if(unsafe != null) {
				moduleOffset = unsafe.objectFieldOffset(module);
			}
		} catch(Throwable e) {
			error = e;
		}
		UNSAFE = unsafeInstance;
		ERROR = error;
		IS_SUPPORTED = module != null && moduleOffset > 0 && moduleOffset < 100;
		MODULE_OFFSET = moduleOffset;
	}
	
	public static void startUnsafe(Class<?> reflector, Class<?> zipfs) {
		((Unsafe)UNSAFE).putObjectVolatile(reflector, MODULE_OFFSET, zipfs.getModule());
	}
	
	public static void endUnsafe(Class<?> reflector, Module original) {
		((Unsafe)UNSAFE).putObjectVolatile(reflector, MODULE_OFFSET, original);
	}
}
