// L� uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import dto.FilePacketInformation;
import util.FileReader;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.zip.CRC32;

import static enums.MessageType.*;

class UDPClient {
   private final static char SEPARATOR = '/';
   private final static Integer PACKET_BYTE_SIZE = 300;
   private final static Integer PORT = 9876;
   private final static CRC32 crcCalculator = new CRC32();

   public static void main(String[] args) throws Exception {
      if (args.length != 2) {
         System.err.println("ERRO: deve informar o nome do arquivo e o endereço IP do servidor, nessa ordem, como argumento");
         System.exit(1);
      }

      // Pega bytes do arquivo a enviar
      byte[] fileBytes = FileReader.readBytesFromFile(args[0]);
      String serverAddress = args[1];

      // Declara socket cliente
      try (DatagramSocket clientSocket = new DatagramSocket()) {
         InetAddress ipServerAddress = InetAddress.getByName(serverAddress);

         byte[] sendData;
         byte[] receiveData = new byte[300];
         DatagramPacket sendPacket;
         DatagramPacket receivePacket;
         int sequenceNumber = 0;
         int fileOffset = 0;
         boolean hasActiveConnection = false;

         // ENVIA SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
         sendData = formatPacketData(sequenceNumber, SYN.name());
         sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);

         System.out.printf("Enviando mensagem para endereco %s:%d --> %s", ipServerAddress.getHostAddress(), PORT, new String(sendData));
         clientSocket.send(sendPacket);

         while(true) {
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);

            InetAddress clientIpAddress = receivePacket.getAddress();
            String receivedMessage = new String(receivePacket.getData());
            int clientPort = receivePacket.getPort();
            System.out.printf("Mensagem recebida de servidor %s:%d --> %s", clientIpAddress.getHostAddress(), clientPort, receivedMessage);

            // VERIFICA CRC
            boolean correctCrc = checkReceivedPacketCrc(receivedMessage);
            if (!correctCrc)
               continue;

            // RECEBEU SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
            if (!hasActiveConnection && receivedMessage.contains(SYNACK.name())) {
               sendData = formatPacketData(sequenceNumber, ACK.name());
               sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);
               System.out.printf("Enviando mensagem para servidor %s:%d --> %s", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
               clientSocket.send(sendPacket);

               hasActiveConnection = true;
            }
            // RECEBEU SOLICITACAO PARA ENCERRAMENTO DE CONEXAO
            else if (hasActiveConnection && receivedMessage.contains(FINACK.name())) {
               sendData = formatPacketData(0, ACK.name());
               sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);
               System.out.printf("Enviando mensagem para servidor %s:%d --> %s", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
               clientSocket.send(sendPacket);

               hasActiveConnection = false;
               sequenceNumber = 0;
               break;
            }
            // ENVIA DADOS
            else if (hasActiveConnection && fileOffset < fileBytes.length) {
               FilePacketInformation newPacketInfo = formatPacketDataForFileTransfer(sequenceNumber, fileBytes, fileOffset);
               sendPacket = new DatagramPacket(newPacketInfo.getData(), newPacketInfo.getData().length, ipServerAddress, PORT);
               System.out.printf("Enviando mensagem para servidor %s:%d --> %s", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
               clientSocket.send(sendPacket);
               fileOffset = newPacketInfo.getFileOffset();
               sequenceNumber += 1;

               // TODO ADD TIMER PARA CADA PACOTE ENVIADO
               // TODO VALIDA ACK PARA ENVIAR DADOS APENAS APOS CONFIRMAR O PROXIMO, VALIDANDO NUMERO DE ACK
               // TODO CONTROLE DE CONGESTIONAMENTO
            }
            // DEVE ENCERRAR A CONEXAO --> ENVIOU TODOS OS DADOS
            else if (hasActiveConnection && fileOffset >= fileBytes.length) {
               sendData = formatPacketData(0, FIN.name());
               sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);
               System.out.printf("Enviando mensagem para servidor %s:%d --> %s", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
               clientSocket.send(sendPacket);
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

      crcCalculator.reset();
      return finalBuffer;
   }

   private static FilePacketInformation formatPacketDataForFileTransfer(int sequenceNumber, byte[] fileData, int fileOffset) {
      // TAMANHO DO PACOTE SEM DADOS: num.seq (4 bytes) + separador (2 bytes) + crc (4 bytes) + separador (2 bytes) --> 12 bytes
      int maxDataBytes = PACKET_BYTE_SIZE - 12;
      byte[] dataBytes;
      int newOffset;

      int remainingFileDataSize = (fileData.length - fileOffset);
      if (remainingFileDataSize < maxDataBytes) { // vai ter que usar o padding
         dataBytes = Arrays.copyOfRange(fileData, fileOffset, maxDataBytes); // preenche com 0s o excesso de bytes
         dataBytes[remainingFileDataSize] = (byte) SEPARATOR; // adicona separador entre dados e padding
         newOffset = fileData.length; // atualiza offset para final de buffer / era ultimo pacote com dados do arquivo
      } else {
         newOffset = fileOffset + maxDataBytes;
         dataBytes = Arrays.copyOfRange(fileData, fileOffset, newOffset); // pega proximo chunk do arquivo
      }

      // Calcula CRC
      crcCalculator.update(dataBytes);
      long calculatedCrc = crcCalculator.getValue();

      crcCalculator.reset();
      // Monta dados do pacote
      String dataStr = String.format("%d%c%d%c%s", sequenceNumber, SEPARATOR, calculatedCrc, SEPARATOR, new String(dataBytes));
      return new FilePacketInformation(dataStr.getBytes(), newOffset);
   }

   private static boolean checkReceivedPacketCrc(String packetData) {
      String[] packetDataParts = packetData.split(String.valueOf(SEPARATOR));
      crcCalculator.update(packetDataParts[2].getBytes());
      long checkCalculatedCrc = crcCalculator.getValue();

      crcCalculator.reset();
      return checkCalculatedCrc == Long.parseLong(packetDataParts[1]);
   }
}