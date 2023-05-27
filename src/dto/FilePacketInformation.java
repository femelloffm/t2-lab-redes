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
