/*----------------------------------------------------------------------------------------*/
/* T2 - Laboratório de Redes de Computadores - Professora Cristina Moreira Nunes - 2023/1 */
/* André Luiz Rodrigues, Fernanda Ferreira de Mello, Matheus Pozzer Moraes                */
/*----------------------------------------------------------------------------------------*/
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
