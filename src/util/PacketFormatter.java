package util;

import dto.FilePacketInformation;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static config.AppConstants.*;

public class PacketFormatter {
    public static byte[] formatPacketData(int sequenceNumber, String data) {
        // Calcula CRC
        byte[] dataBytes = data.getBytes();

        // Define headers
        byte[] headerBytes = buildHeadersByteArray(sequenceNumber, dataBytes);

        // Build packet from header and data byte arrays
        byte[] packetBytesWithoutDataPadding = joinHeaderAndDataByteArrays(headerBytes, dataBytes);
        byte[] finalBuffer = Arrays.copyOf(packetBytesWithoutDataPadding, PACKET_BYTE_SIZE);

        // Se dados do pacote foram menores que 300
        if (packetBytesWithoutDataPadding.length < PACKET_BYTE_SIZE) {
            // Adicona separadoR entre dados e padding, conforme --> num seq/crc/padding headers/texto/padding texto
            finalBuffer[packetBytesWithoutDataPadding.length] = (byte) SEPARATOR;
        }

        return finalBuffer;
    }

    public static FilePacketInformation formatPacketDataForFileTransfer(int sequenceNumber, byte[] fileData, int fileOffset) {
        byte[] dataBytes;
        byte[] dataBytesWithPadding;
        int newOffset;

        int remainingFileDataSize = (fileData.length - fileOffset);
        // Se precisar enviar mensagem com padding (a ultima mensagem com dados do arquivo)
        if (remainingFileDataSize < MAX_DATA_BYTE_SIZE) {
            int tempMaxSize = fileOffset + MAX_DATA_BYTE_SIZE;
            dataBytes = Arrays.copyOfRange(fileData, fileOffset, fileData.length); // Bytes restantes do arquivo
            dataBytesWithPadding = Arrays.copyOfRange(fileData, fileOffset, tempMaxSize); // Bytes restantes do arquivo com excesso preenchido com zeros
            dataBytesWithPadding[remainingFileDataSize] = (byte) SEPARATOR; // Adiciona separador entre dados e padding
            newOffset = fileData.length; // Atualiza offset para final de buffer do arquivo
        } else {
            newOffset = fileOffset + MAX_DATA_BYTE_SIZE;
            dataBytes = Arrays.copyOfRange(fileData, fileOffset, newOffset); // Copia proximo chunk do arquivo
            dataBytesWithPadding = dataBytes;
        }

        // Define headers
        byte[] headerBytes = buildHeadersByteArray(sequenceNumber, dataBytes);
        // Build packet from header and data byte arrays
        byte[] finalPacketBytes = joinHeaderAndDataByteArrays(headerBytes, dataBytesWithPadding);

        return new FilePacketInformation(finalPacketBytes, newOffset);
    }

    private static byte[] buildHeadersByteArray(int sequenceNumber, byte[] dataBytes) {
        // Calcula CRC
        long calculatedCrc = CrcCalculator.calculateCrc(dataBytes);

        // Define headers
        String headerStr = String.format("%d%c%d%c", sequenceNumber, SEPARATOR, calculatedCrc, SEPARATOR);
        byte[] headerBytes = new byte[HEADERS_BYTE_SIZE];
        byte[] headerStrAsBytes = headerStr.getBytes();

        // Copia headers para array de HEADERS_BYTE_SIZE (headers devem sempre ter o mesmo tamanho fixo)
        for (int i = 0; i < headerBytes.length; i++) {
            if (headerStrAsBytes.length > i) {
                headerBytes[i] = headerStrAsBytes[i];
            } else {
                break;
            }
        }

        // Adiciona padding entre crc e padding se precisa de padding nos headers
        if (headerStrAsBytes.length < headerBytes.length) {
            headerBytes[headerBytes.length - 1] = (byte) SEPARATOR;
        }

        return headerBytes;
    }

    private static byte[] joinHeaderAndDataByteArrays(byte[] headerBytes, byte[] dataBytes) {
        byte[] jointArray = new byte[headerBytes.length + dataBytes.length];
        ByteBuffer packetBuffer = ByteBuffer.wrap(jointArray);
        packetBuffer.put(headerBytes);
        packetBuffer.put(dataBytes);
        return packetBuffer.array();
    }
}
