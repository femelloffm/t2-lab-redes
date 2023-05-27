// L� uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import dto.FilePacketInformation;
import util.FileReader;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.zip.CRC32;

class UDPClient {
   private final static char SEPARATOR = '/';
   private final static CRC32 crcCalculator = new CRC32();
   private final static Integer PACKET_BYTE_SIZE = 300;

   public static void main(String args[]) throws Exception {
      if (args.length != 2) {
         System.err.println("ERRO: deve informar o nome do arquivo e o endereço IP do servidor, nessa ordem, como argumento");
         System.exit(1);
      }

      // Pega bytes do arquivo a enviar
      byte[] fileBytes = FileReader.readBytesFromFile(args[0]);
      int fileOffset = 0;
      String serverAddress = args[1];

      // Declara socket cliente
      try (DatagramSocket clientSocket = new DatagramSocket()) {

         InetAddress ipServerAddress = InetAddress.getByName(serverAddress);

         byte[] sendData;
         byte[] receiveData = new byte[300];
         DatagramPacket sendPacket;
         DatagramPacket receivePacket;

         // ESTABELECIMENTO DE CONEXAO -----------------------------------------------------------------------------------
         // Envio de solicitacao para estabelecer conexao
         sendData = formatPacketData(0, "SYN");
         sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, 9876);
         clientSocket.send(sendPacket);
         // Recebe solicitao para estabelecer conexao
         receivePacket = new DatagramPacket(receiveData, receiveData.length);
         clientSocket.receive(receivePacket);
         String receivedSentence = new String(receivePacket.getData());
         System.out.println("Mensagem recebida: " + receivedSentence);

         if (receivedSentence.contains("SYNACK")) {
            // vai primeiro enviar um ACK
            sendData = formatPacketData(0, "ACK");
            sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, 9876);
            clientSocket.send(sendPacket);

            // e depois entra no fluxo de ir enviando o arquivo
            while (fileOffset < fileBytes.length) {

            }
         }
      }
   }

   private static byte[] formatPacketData(int sequenceNumber, String data) {
      // Calcula CRC
      crcCalculator.update(data.getBytes());
      long calculatedCrc = crcCalculator.getValue();

      // Monta dados do pacote --> num.sequencia/CRC/dados
      String dataStr = String.format("%d%c%d%c%s", sequenceNumber, SEPARATOR, calculatedCrc, SEPARATOR, data);
      int dataStrSize = dataStr.getBytes().length;
      byte[] finalBuffer = Arrays.copyOf(dataStr.getBytes(), PACKET_BYTE_SIZE);

      // Se dados do pacote foram menores que 300
      if (dataStrSize < PACKET_BYTE_SIZE) {
         // Adicona separados entre dados e padding, conforme --> num.sequencia/CRC/dados/padding
         finalBuffer[dataStrSize] = (byte) SEPARATOR;
      }

      return finalBuffer;
   }

   private static FilePacketInformation formatPacketDataFile(int sequenceNumber, byte[] fileData, int offset) {
      // TAMANHO DO PACOTE SEM DADOS: num.seq (4 bytes) + separador (2 bytes) + crc (4 bytes) + separador (2 bytes) --> 12 bytes
      int maxDataBytes = PACKET_BYTE_SIZE - 12;
      byte[] dataBytes;
      int newOffset;

      int remainingFileDataSize = (fileData.length - offset);
      if (remainingFileDataSize < maxDataBytes) { // vai ter que usar o padding
         dataBytes = Arrays.copyOfRange(fileData, offset, maxDataBytes); // preenche com 0s o excesso de bytes
         dataBytes[remainingFileDataSize] = (byte) SEPARATOR; // adicona separador entre dados e padding
         newOffset = fileData.length; // atualiza offset para final de buffer / era ultimo pacote com dados do arquivo
      } else {
         newOffset = offset + maxDataBytes;
         dataBytes = Arrays.copyOfRange(fileData, offset, newOffset); // pega proximo chunk do arquivo
      }

      // Calcula CRC
      crcCalculator.update(dataBytes);
      long calculatedCrc = crcCalculator.getValue();

      // Monta dados do pacote
      String dataStr = String.format("%d%c%d%c%s", sequenceNumber, SEPARATOR, calculatedCrc, SEPARATOR, new String(dataBytes));
      return new FilePacketInformation(dataStr.getBytes(), newOffset);
   }
}