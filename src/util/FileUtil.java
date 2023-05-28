package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileUtil {
    public static byte[] readBytesFromFile(String fileName) throws IOException {
        File file = new File(fileName);
        return Files.readAllBytes(file.toPath());
    }

    public static void writeBytesToFile(String fileName, byte[] data) throws IOException {
        File file = new File(fileName);
        Files.write(file.toPath(), data);
    }
}
