/*----------------------------------------------------------------------------------------*/
/* T2 - Laboratório de Redes de Computadores - Professora Cristina Moreira Nunes - 2023/1 */
/* André Luiz Rodrigues, Fernanda Ferreira de Mello, Matheus Pozzer Moraes                */
/*----------------------------------------------------------------------------------------*/
package dto;

public class FilePacketInformation {
    private byte[] data;
    private int fileOffset;

    public FilePacketInformation(byte[] data, int fileOffset) {
        this.data = data;
        this.fileOffset = fileOffset;
    }

    public byte[] getData() {
        return data;
    }

    public int getFileOffset() {
        return fileOffset;
    }
}
