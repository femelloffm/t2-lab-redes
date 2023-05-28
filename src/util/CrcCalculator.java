package util;

import java.util.zip.CRC32;

public class CrcCalculator {
    private final static CRC32 crcCalculator = new CRC32();

    public static long calculateCrc(byte[] data) {
        crcCalculator.update(data);
        long checkCalculatedCrc = crcCalculator.getValue();

        crcCalculator.reset();
        return checkCalculatedCrc;
    }

    public static boolean checkReceivedPacketCrc(long expectedCrc, byte[] data) {
        crcCalculator.update(data);
        long checkCalculatedCrc = crcCalculator.getValue();

        crcCalculator.reset();
        return checkCalculatedCrc == expectedCrc;
    }
}
