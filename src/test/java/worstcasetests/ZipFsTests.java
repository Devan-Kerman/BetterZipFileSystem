package worstcasetests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import net.devtech.betterzipfs.ZipFS;

public class ZipFsTests {
	public static void main(String[] args) throws IOException {
		try(FileSystem src = new ZipFS(FileSystems.newFileSystem(Path.of("test.jar"), Map.of("create", "true"))); FileSystem dst = new ZipFS(FileSystems.newFileSystem(Path.of("out.jar"), Map.of("create", "true")))) {
			Path path = src.getPath("test.txt");
			String test = "hello my friends, how do you do?";
			Files.writeString(path, test, StandardCharsets.UTF_8);
			try(BufferedReader is = Files.newBufferedReader(path)) {
				System.out.println(is.readLine());
			}
			Files.copy(path, dst.getPath("dst.txt"), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
