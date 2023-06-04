// Recebe um pacote de algum cliente
// Separa o dado, o endere�o IP e a porta deste cliente
// Imprime o dado na tela

import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static config.AppConstants.SEPARATOR;
import static config.AppConstants.SERVER_PORT;
import static enums.MessageType.*;
import static util.PacketFormatter.formatPacketData;

class UDPServer {

    public static void main(String[] args) {

        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {

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
}