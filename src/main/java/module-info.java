import net.devtech.betterzipfs.ZipFSProvider;

module net.devtech.betterzipfs {
	requires jdk.unsupported;
	provides java.nio.file.spi.FileSystemProvider with ZipFSProvider;
}