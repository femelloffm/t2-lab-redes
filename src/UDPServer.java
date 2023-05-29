// Recebe um pacote de algum cliente
// Separa o dado, o endere�o IP e a porta deste cliente
// Imprime o dado na tela

import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import static enums.MessageType.*;

class UDPServer {
    private final static char SEPARATOR = '/';
    private final static Integer PACKET_BYTE_SIZE = 300;
    private final static Integer PORT = 9876;


    public static void main(String[] args) {

        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {

            byte[] sendData;
            byte[] receiveData = new byte[300];
            boolean hasActiveConnection = false;
            int ackNumber = 0;

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
                boolean correctCrc = CrcCalculator.checkReceivedPacketCrc(Long.parseLong(packetDataParts[1]), packetDataParts[2].getBytes());
                if (!correctCrc){
                    continue;
                }

                // RECEBEU SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
                if (!hasActiveConnection && packetDataParts[2].equals(SYN.name())) {
                    sendData = formatPacketData(ackNumber, SYNACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);

                    System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    serverSocket.send(sendPacket);
                    hasActiveConnection = true;
                }
                // RECEBEU SOLICITACAO PARA ENCERRAMENTO DE CONEXAO
                else if (hasActiveConnection && packetDataParts[2].equals(FIN.name())) {
                    // TODO enviar FINACK
                    hasActiveConnection = false;
                    ackNumber = 0;
                    sendData = formatPacketData(ackNumber, FINACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                    serverSocket.send(sendPacket);
                }
                // RECEBEU DADOS
                else if (hasActiveConnection && !packetDataParts[2].equals(ACK.name())) {
                    int receivedSequenceNumber = Integer.parseInt(packetDataParts[0]);
                    ackNumber = receivedSequenceNumber + 1;
                    // TODO se recebe um pacote com num de sequencia X mas um de numero de sequencia menor nao foi recebido, transmite um ACK com o numero de sequencia do ultimo pacote confirmado
                    FileUtil.writeBytesToFile("teste2.txt", packetDataParts[2].getBytes());

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

    private static byte[] formatPacketData(int ackNumber, String data) {
        // Calcula CRC
        long calculatedCrc = CrcCalculator.calculateCrc(data.getBytes());

        // Monta dados do pacote --> num.ack/CRC/dados
        String dataStr = String.format("%d%c%d%c%s", ackNumber, SEPARATOR, calculatedCrc, SEPARATOR, data);
        int dataStrSize = dataStr.getBytes().length;
        byte[] finalBuffer = Arrays.copyOf(dataStr.getBytes(), PACKET_BYTE_SIZE);

        // Se dados do pacote foram menores que 300

        if (dataStrSize < PACKET_BYTE_SIZE) {
            // Adicona separados entre dados e padding, conforme --> num.sequencia/CRC/dados/padding
            finalBuffer[dataStrSize] = (byte) SEPARATOR;
        }

        return finalBuffer;
    }
}