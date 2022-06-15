package net.devtech.betterzipfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;

public class ZipTest {
	public static void main(String[] args) {
		System.out.println(FileSystemProvider.installedProviders());
		try(FileSystem src = ZipFS.createZip(Path.of("test.jar")); FileSystem dst = ZipFS.createZip(Path.of("out.jar"))) {
			Path path = src.getPath("test.txt");
			String test = "hello my friends, how do you do?";
			Files.writeString(path, test, StandardCharsets.UTF_8);
			try(BufferedReader is = Files.newBufferedReader(path)) {
				System.out.println(is.readLine());
			}
			Files.copy(path, dst.getPath("dst.txt"), StandardCopyOption.REPLACE_EXISTING);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
