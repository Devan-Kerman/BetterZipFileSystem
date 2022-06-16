package worstcasetests;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;

import net.devtech.betterzipfs.ZipFS;

public class ZipFsTests {
	public static void main(String[] args) throws IOException {
		System.out.println(FileSystemProvider.installedProviders());
		try(FileSystem src = ZipFS.createZip(Path.of("test.jar")); FileSystem dst = ZipFS.createZip(Path.of("out.jar"))) {
			String test = "hello my friends, how do you do?";
			Files.writeString(src.getPath("/test.txt"), test, StandardCharsets.UTF_8);
			//Files.copy(path, dst.getPath("dst.txt"), StandardCopyOption.REPLACE_EXISTING);
			Files.copy(src.getPath("test.txt"), dst.getPath("dst.txt"), StandardCopyOption.REPLACE_EXISTING);
			try(BufferedReader is = Files.newBufferedReader(dst.getPath("/dst.txt"))) {
				System.out.println(is.readLine());
			}
		}
	}
}
