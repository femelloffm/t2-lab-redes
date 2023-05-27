package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileReader {
    public static byte[] readBytesFromFile(String fileName) throws IOException {
        File file = new File(fileName);
        return Files.readAllBytes(file.toPath());
    }
}
