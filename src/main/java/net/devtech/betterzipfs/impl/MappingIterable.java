package net.devtech.betterzipfs.impl;

import java.util.function.Function;

record MappingIterable<F, T>(Iterable<F> src, Function<F, T> converter) implements Iterable<T> {
	
	@Override
	public java.util.Iterator<T> iterator() {
		return new Iterator<>(this.src.iterator(), this.converter);
	}
	
	public record Iterator<F, T>(java.util.Iterator<F> src, Function<F, T> converter) implements java.util.Iterator<T> {
		
		@Override
		public boolean hasNext() {
			return this.src.hasNext();
		}
		
		@Override
		public T next() {
			return this.converter.apply(this.src.next());
		}
	}
}