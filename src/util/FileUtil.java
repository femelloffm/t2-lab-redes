/*----------------------------------------------------------------------------------------*/
/* T2 - Laboratório de Redes de Computadores - Professora Cristina Moreira Nunes - 2023/1 */
/* André Luiz Rodrigues, Fernanda Ferreira de Mello, Matheus Pozzer Moraes                */
/*----------------------------------------------------------------------------------------*/
package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class FileUtil {
    public static byte[] readBytesFromFile(String fileName) throws IOException {
        File file = new File(fileName);
        return Files.readAllBytes(file.toPath());
    }

    public static void writeBytesToFile(String fileName, byte[] data) throws IOException {
        File file = new File(fileName);
        if(!file.exists()){
            Files.write(file.toPath(), data);
        }
        else{
            Files.write(file.toPath(), data, StandardOpenOption.APPEND);
        }
    }
}
