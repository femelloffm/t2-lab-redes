// Recebe um pacote de algum cliente
// Separa o dado, o endere�o IP e a porta deste cliente
// Imprime o dado na tela

import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static enums.MessageType.*;

class UDPServer {
    private final static char SEPARATOR = '/';
    private final static Integer PACKET_BYTE_SIZE = 300;
    private final static Integer HEADERS_BYTE_SIZE = 20;
    private final static Integer PORT = 9876;


    public static void main(String[] args) {

        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {

            byte[] sendData;
            byte[] receiveData = new byte[300];
            boolean hasActiveConnection = false;
            int ackNumber = 0;
            int connectionNumber = 0;

            while (true) {
                // RECEBE NOVA MENSAGEM
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);

                InetAddress clientIpAddress = receivePacket.getAddress();
                String receivedMessage = new String(receivePacket.getData());
                int clientPort = receivePacket.getPort();
                System.out.printf("Mensagem recebida de endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, receivedMessage);

                // VERIFICA CRC
                String[] packetDataParts = receivedMessage.split(String.valueOf(SEPARATOR));
                boolean correctCrc = CrcCalculator.checkReceivedPacketCrc(Long.parseLong(packetDataParts[1]), packetDataParts[3].getBytes());
                if (!correctCrc) {
                    System.out.println("CRC calculado difere do CRC esperado. Descartando pacote...");
                    continue;
                }

                // RECEBEU SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
                if (!hasActiveConnection && packetDataParts[3].equals(SYN.name())) {
                    sendData = formatPacketData(ackNumber, SYNACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                    System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    serverSocket.send(sendPacket);
                    hasActiveConnection = true;
                    connectionNumber += 1;
                }
                // RECEBEU SOLICITACAO PARA ENCERRAMENTO DE CONEXAO
                else if (hasActiveConnection && packetDataParts[3].equals(FIN.name())) {
                    hasActiveConnection = false;
                    ackNumber = 0;
                    sendData = formatPacketData(ackNumber, FINACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                    System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    serverSocket.send(sendPacket);
                }
                // RECEBEU DADOS
                else if (hasActiveConnection && !packetDataParts[3].equals(ACK.name())) {
                    int receivedSequenceNumber = Integer.parseInt(packetDataParts[0]);
                    ackNumber = receivedSequenceNumber + 1;
                    // TODO se recebe um pacote com num de sequencia X mas um de numero de sequencia menor nao foi recebido, transmite um ACK com o numero de sequencia do ultimo pacote confirmado
                    FileUtil.writeBytesToFile(String.format("copia%d.txt", connectionNumber), packetDataParts[3].getBytes());

                    sendData = formatPacketData(ackNumber, ACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);

                    System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    serverSocket.send(sendPacket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] formatPacketData(int sequenceNumber, String data) {
        // Calcula CRC
        byte[] dataBytes = data.getBytes();
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

        // Build packet from header and data byte arrays
        byte[] finalPacketBytesWithoutDataPadding = new byte[headerBytes.length + dataBytes.length];
        ByteBuffer packetBuffer = ByteBuffer.wrap(finalPacketBytesWithoutDataPadding);
        packetBuffer.put(headerBytes);
        packetBuffer.put(dataBytes);

        int dataStrSize = finalPacketBytesWithoutDataPadding.length;
        byte[] finalBuffer = Arrays.copyOf(packetBuffer.array(), PACKET_BYTE_SIZE);
        // Se dados do pacote foram menores que 300
        if (dataStrSize < PACKET_BYTE_SIZE) {
            // Adicona separadoR entre dados e padding, conforme --> num seq/crc/padding headers/texto/padding texto
            finalBuffer[dataStrSize] = (byte) SEPARATOR;
        }

        return finalBuffer;
    }
}