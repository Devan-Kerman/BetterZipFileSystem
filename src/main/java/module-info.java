import net.devtech.betterzipfs.impl.ZipFSProvider;

module net.devtech.betterzipfs {
	requires jdk.unsupported;
	provides java.nio.file.spi.FileSystemProvider with ZipFSProvider;
}