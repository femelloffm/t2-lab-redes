// L� uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import dto.FilePacketInformation;
import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static config.AppConstants.*;
import static enums.MessageType.*;
import static util.PacketFormatter.formatPacketData;
import static util.PacketFormatter.formatPacketDataForFileTransfer;

class UDPClient {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                    "ERRO: deve informar o nome do arquivo e o endereço IP do servidor, nessa ordem, como argumento");
            System.exit(1);
        }

        // Pega bytes do arquivo a enviar
        byte[] fileBytes = FileUtil.readBytesFromFile(args[0]);
        System.out.println("Going to send file " + args[0] + ". File has " + fileBytes.length + " bytes");
        String serverAddress = args[1];
        Map<Integer, Long> timerByPacket = new HashMap<>();
        Map<Integer, byte[]> dataByPacket = new HashMap<>();
        int cwnd = 1;

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
            sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, SERVER_PORT);

            System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", ipServerAddress.getHostAddress(),
                    SERVER_PORT, new String(sendData));
            clientSocket.send(sendPacket);
            timerByPacket.put(0, System.currentTimeMillis());
            dataByPacket.put(0, sendData);

            while (true) {
                Thread.sleep(SLEEP_TIME);
                if (timerByPacket.size() > 0) {
                    System.out.println("Horario de envio (ms) de pacotes aguardando ACK:");
                    timerByPacket.forEach((sentSequenceNumber, sendTime) -> {
                        System.out.printf("Num Seq.: %d - Horario: %d\n", sentSequenceNumber, sendTime);
                        // Se passou do timeout, deve retransmitir
                        if ((System.currentTimeMillis() - sendTime) >= ACK_TIMEOUT) {
                            System.out.println("\tTimeout de pacote " + sentSequenceNumber + " alcancado");
                            byte[] bytesToRetransmit = dataByPacket.get(sentSequenceNumber);
                            DatagramPacket retransmitPacket = new DatagramPacket(bytesToRetransmit,
                                    bytesToRetransmit.length, ipServerAddress, SERVER_PORT);
                            System.out.printf("\tRetransmitindo mensagem para servidor %s:%d --> %s\n",
                                    ipServerAddress.getHostAddress(), SERVER_PORT, new String(bytesToRetransmit));
                            try {
                                clientSocket.send(retransmitPacket);
                            } catch (IOException e) {
                                System.err.println("\tRetransmissou falhou: " + e);
                            }
                            timerByPacket.put(sentSequenceNumber, System.currentTimeMillis());
                        }
                    });
                    System.out.println("--------------------------------");
                }

                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);

                InetAddress clientIpAddress = receivePacket.getAddress();
                String receivedMessage = new String(receivePacket.getData());
                int clientPort = receivePacket.getPort();
                System.out.printf("Mensagem recebida de servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(),
                        clientPort, receivedMessage);

                // VERIFICA CRC
                String[] packetDataParts = receivedMessage.split(String.valueOf(SEPARATOR));
                boolean correctCrc = CrcCalculator.checkReceivedPacketCrc(Long.parseLong(packetDataParts[1]),
                        packetDataParts[3].getBytes());
                if (!correctCrc) {
                    System.out.println("CRC calculado difere do CRC esperado. Descartando pacote...");
                    continue;
                }

                // RECEBEU SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
                if (!hasActiveConnection && packetDataParts[3].equals(SYNACK.name())) {
                    sendData = formatPacketData(sequenceNumber, ACK.name());
                    sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, SERVER_PORT);
                    System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n",
                            clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    clientSocket.send(sendPacket);

                    hasActiveConnection = true;
                    timerByPacket.put(sequenceNumber, System.currentTimeMillis());
                    dataByPacket.put(sequenceNumber, sendData);
                    int ackNumber = Integer.parseInt(packetDataParts[0]);
                    timerByPacket.remove(ackNumber);
                    dataByPacket.remove(ackNumber);
                    System.out.println("Recebeu SYNACK. Parando timer de pacote SYN enviado...");
                }
                // RECEBEU SOLICITACAO PARA ENCERRAMENTO DE CONEXAO
                else if (hasActiveConnection && packetDataParts[3].equals(FINACK.name())) {
                    sendData = formatPacketData(0, ACK.name());
                    sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, SERVER_PORT);
                    System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n",
                            clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    clientSocket.send(sendPacket);

                    hasActiveConnection = false;
                    sequenceNumber = 0;
                    timerByPacket.clear();
                    dataByPacket.clear();
                    break;
                } else if (hasActiveConnection && packetDataParts[3].equals(ACK.name())) {
                    // numero de ack recebido = numero de sequencia + 1
                    int ackNumber = Integer.parseInt(packetDataParts[0]);
                    timerByPacket.remove(ackNumber - 1);
                    dataByPacket.remove(ackNumber - 1);
                    System.out.println("Recebeu ACK. Parando timer de pacote enviado com num de sequencia "
                            + (ackNumber - 1) + "...");
                }

                // ENVIA DADOS
                if (hasActiveConnection && fileOffset < fileBytes.length) {
                    for (int i = 0; i < cwnd; i++) {
                        FilePacketInformation newPacketInfo = formatPacketDataForFileTransfer(sequenceNumber, fileBytes,
                                fileOffset);
                        sendPacket = new DatagramPacket(newPacketInfo.getData(), newPacketInfo.getData().length,
                                ipServerAddress, SERVER_PORT);
                        System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n",
                                clientIpAddress.getHostAddress(), clientPort, new String(newPacketInfo.getData()));
                        clientSocket.send(sendPacket);
                        fileOffset = newPacketInfo.getFileOffset();
                        timerByPacket.put(sequenceNumber, System.currentTimeMillis());
                        dataByPacket.put(sequenceNumber, newPacketInfo.getData());
                        sequenceNumber += 1;
                        if (hasActiveConnection && fileOffset >= fileBytes.length) {
                            endConnection(hasActiveConnection, fileOffset, clientSocket, timerByPacket, dataByPacket,
                                    ipServerAddress, clientIpAddress, sequenceNumber, clientPort);
                                    break;
                        }
                    }
                    if (cwnd < THRESHOLD) {
                        cwnd = cwnd * 2;
                    }
                    // TODO VALIDA ACK PARA ENVIAR DADOS APENAS APOS CONFIRMAR O PROXIMO, VALIDANDO
                    // NUMERO DE ACK
                    // TODO CONTROLE DE CONGESTIONAMENTO
                }
                // DEVE ENCERRAR A CONEXAO --> ENVIOU TODOS OS DADOS
                else if (hasActiveConnection && fileOffset >= fileBytes.length) {
                    endConnection(hasActiveConnection, fileOffset, clientSocket, timerByPacket, dataByPacket,
                            ipServerAddress, clientIpAddress, sequenceNumber, clientPort);
                }
            }
        }
    }

    private static void endConnection(boolean hasActiveConnection, int fileOffset, DatagramSocket clientSocket,
            Map<Integer, Long> timerByPacket, Map<Integer, byte[]> dataByPacket, InetAddress ipServerAddress,
            InetAddress clientIpAddress, int sequenceNumber, int clientPort) throws IOException {
        byte[] sendData = formatPacketData(0, FIN.name());
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, SERVER_PORT);
        System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(),
                clientPort, new String(sendData));
        clientSocket.send(sendPacket);
        timerByPacket.put(sequenceNumber, System.currentTimeMillis());
        dataByPacket.put(sequenceNumber, sendData);
    }
}