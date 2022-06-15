package net.devtech.betterzipfs.util;

import java.util.Arrays;

public record ByteArrayWrapper(byte[] arr) {
	@Override
	public boolean equals(Object o) {
		return this == o || o instanceof ByteArrayWrapper wrapper && Arrays.equals(this.arr, wrapper.arr);
		
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(this.arr);
	}
}