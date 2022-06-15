# BetterZipFileSystem
ZipFileSystem but it doesn't unessesarily decompress/recompress files

## Problem

The following code is ineffecient with the default ZipFileSystem
```java
package worstcasetests;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class ZipFsTests {
	public static void main(String[] args) throws IOException {
		try(FileSystem src = ...; FileSystem dst = ...) {
			Path path = src.getPath("test.txt");
			String test = "hello my friends, how do you do?";
			Files.writeString(path, test, StandardCharsets.UTF_8); // compresses the string into the zip
			try(BufferedReader is = Files.newBufferedReader(path)) { // decompresses the same string
				System.out.println(is.readLine());
			}
			Files.copy(path, dst.getPath("dst.txt"), StandardCopyOption.REPLACE_EXISTING); // decompresses then recompresses the same string
		}
	}
}
```

BetterZipFileSystem was made to address this problem, and it does
