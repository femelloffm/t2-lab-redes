/*----------------------------------------------------------------------------------------*/
/* T2 - Laboratório de Redes de Computadores - Professora Cristina Moreira Nunes - 2023/1 */
/* André Luiz Rodrigues, Fernanda Ferreira de Mello, Matheus Pozzer Moraes                */
/*----------------------------------------------------------------------------------------*/

import dto.FilePacketInformation;
import enums.CongestionControlType;
import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static config.AppConstants.*;
import static enums.MessageType.*;
import static util.PacketFormatter.formatPacketData;
import static util.PacketFormatter.formatPacketDataForFileTransfer;

class UDPClient {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("ERRO: deve informar o nome do arquivo e o endereço IP do servidor, nessa ordem, como argumento");
            System.exit(1);
        }

        // Pega bytes do arquivo a enviar
        byte[] fileBytes = FileUtil.readBytesFromFile(args[0]);
        System.out.println("Vai enviar arquivo " + args[0] + ". Arquivo possui " + fileBytes.length + " bytes");
        String serverAddress = args[1];
        Map<Integer, Long> timerByPacket = new HashMap<>();
        Map<Integer, byte[]> dataByPacket = new HashMap<>();
        int cwnd = 1;
        CongestionControlType currentCongestionControlType = CongestionControlType.SLOW_START;

        // Declara socket cliente
        try (DatagramSocket clientSocket = new DatagramSocket()) {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT);
            InetAddress serverIpAddress = InetAddress.getByName(serverAddress);

            byte[] sendData;
            byte[] receiveData = new byte[300];
            DatagramPacket receivePacket;
            int sequenceNumber = 0;
            int fileOffset = 0;
            boolean hasActiveConnection = false;
            int lastAckNumber = 0;
            Map<Integer, Integer> receiveCountByAckNumber = new HashMap<>();

            // ENVIA SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
            sendData = formatPacketData(sequenceNumber, SYN.name());
            System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", serverIpAddress.getHostAddress(),
                    SERVER_PORT, new String(sendData));
            sendMessage(sendData, serverIpAddress, sequenceNumber, clientSocket, timerByPacket, dataByPacket);

            while (true) {
                Thread.sleep(SLEEP_TIME);

                if (timerByPacket.size() > 0) {
                    // Verifica se a espera de um dos ACKs alcancou o timeout e, se sim, retransmite o pacote
                    System.out.println("      Horario de envio (ms) de pacotes aguardando ACK:");
                    AtomicBoolean timeoutOccurred = new AtomicBoolean(false);
                    timerByPacket.forEach((key, value) -> {
                        int sentSequenceNumber = key;
                        long sendTime = value;
                        System.out.printf("      - Num Seq.: %d - Horario: %d\n", sentSequenceNumber, sendTime);
                        // Se passou do timeout, deve retransmitir
                        if ((System.currentTimeMillis() - sendTime) >= ACK_TIMEOUT) {
                            System.out.println("      Timeout de pacote " + sentSequenceNumber + " alcancado");
                            byte[] bytesToRetransmit = dataByPacket.get(sentSequenceNumber);
                            System.out.printf("      Retransmitindo mensagem para servidor %s:%d --> %s\n",
                                    serverIpAddress.getHostAddress(), SERVER_PORT, new String(bytesToRetransmit));
                            try {
                                sendMessage(bytesToRetransmit, serverIpAddress, sentSequenceNumber, clientSocket, timerByPacket, dataByPacket);
                            } catch (IOException e) {
                                System.err.println("      Retransmissou falhou: " + e);
                            }
                            timeoutOccurred.set(true);
                        }
                    });

                    if (timeoutOccurred.get()) { // Se ocorre timeout, volta para o slow start com janela de congestionamento 1
                        System.out.println("Timeout ocorreu enquanto aguardava o ACK. Voltando para o SLOW START...");
                        currentCongestionControlType = CongestionControlType.SLOW_START;
                        cwnd = 1;
                        byte[] restartData = formatPacketData(0, RESTART.name());
                        sendMessage(restartData, serverIpAddress, 0, clientSocket, timerByPacket, dataByPacket);
                    }
                }

                try {
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
                        System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n",
                                clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                        sendMessage(sendData, serverIpAddress, sequenceNumber, clientSocket, timerByPacket, dataByPacket);

                        hasActiveConnection = true;
                        int ackNumber = Integer.parseInt(packetDataParts[0]);
                        timerByPacket.remove(ackNumber);
                        dataByPacket.remove(ackNumber);
                        System.out.println("Recebeu SYNACK. Parando timer de pacote SYN enviado...");
                    }
                    // RECEBEU SOLICITACAO PARA ENCERRAMENTO DE CONEXAO
                    else if (hasActiveConnection && packetDataParts[3].equals(FINACK.name())) {
                        sendData = formatPacketData(0, ACK.name());
                        System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                        sendMessage(sendData, serverIpAddress, sequenceNumber, clientSocket, timerByPacket, dataByPacket);
                        timerByPacket.clear();
                        dataByPacket.clear();
                        break;
                    } else if (hasActiveConnection && packetDataParts[3].equals(ACK.name())) {
                        // numero de ack recebido = numero de sequencia + 1
                        int ackNumber = Integer.parseInt(packetDataParts[0]);
                        lastAckNumber = ackNumber;
                        System.out.println("Recebeu ACK. Parando timers de pacote enviado com num de sequencia igual ou menor a " + (ackNumber - 1) + "...");
                        timerByPacket.entrySet().removeIf(timerEntry -> timerEntry.getKey() <= ackNumber - 1);
                        dataByPacket.entrySet().removeIf(dataEntry -> dataEntry.getKey() <= ackNumber - 1);
                        // Cada vez que recebe um ack, incrementa o contador daquele numero de ack em 1
                        receiveCountByAckNumber.put(ackNumber, receiveCountByAckNumber.getOrDefault(ackNumber, 0) + 1);
                        // Se recebeu 3 acks duplicados
                        if (receiveCountByAckNumber.get(ackNumber) == 3) { // FAST RETRANSMIT
                            System.out.printf("Recebeu 3 ACKs duplicados para o numero de ACK %d. Entrando em fast retransmit...\n", ackNumber);
                            // Retransmite somente pacote identificado pelo ACK
                            byte[] bytesToRetransmit = dataByPacket.get(ackNumber);
                            System.out.printf("Retransmitindo mensagem para servidor %s:%d --> %s\n",
                                    serverIpAddress.getHostAddress(), SERVER_PORT, new String(bytesToRetransmit));
                            sendMessage(bytesToRetransmit, serverIpAddress, ackNumber, clientSocket, timerByPacket, dataByPacket);
                            // O tamanho da janela de congestionamento cai pela metade
                            cwnd = cwnd / 2;
                            currentCongestionControlType = CongestionControlType.CONGESTION_AVOIDANCE;
                        }
                    }

                    // ENVIA DADOS
                    boolean allPacketsWereAcked = lastAckNumber == sequenceNumber; // so envia a proxima leva de dados se tudo ja enviado foi confirmado

                    if (hasActiveConnection && allPacketsWereAcked && fileOffset < fileBytes.length) {
                        System.out.printf("\nVai enviar dados do arquivo. Mecanismo de controle de congestionamento: %s  Janela de congestionamento: %d\n",
                                currentCongestionControlType, cwnd);
                        for (int i = 0; i < cwnd; i++) {
                            FilePacketInformation newPacketInfo = formatPacketDataForFileTransfer(sequenceNumber, fileBytes,
                                    fileOffset);
                            System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n",
                                    clientIpAddress.getHostAddress(), clientPort, new String(newPacketInfo.getData()));
                            sendMessage(newPacketInfo.getData(), serverIpAddress, sequenceNumber, clientSocket, timerByPacket, dataByPacket);

                            fileOffset = newPacketInfo.getFileOffset();
                            sequenceNumber += 1;

                            if (fileOffset >= fileBytes.length) {
                                break;
                            }
                        }

                        // Se é slow start e ainda nao atingiu o limite, aumenta exponencialmente a janela de congestionamento
                        if (currentCongestionControlType.equals(CongestionControlType.SLOW_START) && cwnd < THRESHOLD) {
                            cwnd = cwnd * 2;
                        }
                        // Se é slow start e atingiu o limite, altera tecnica para congestion avoidance
                        else if (currentCongestionControlType.equals(CongestionControlType.SLOW_START)) {
                            currentCongestionControlType = CongestionControlType.CONGESTION_AVOIDANCE;
                        }

                        // Se é congestion avoidance, aumenta incrementalmente a janela de congestionamento
                        if (currentCongestionControlType.equals(CongestionControlType.CONGESTION_AVOIDANCE)) {
                            cwnd += 1;
                        }
                    }
                    // DEVE ENCERRAR A CONEXAO --> ENVIOU TODOS OS DADOS
                    else if (hasActiveConnection && allPacketsWereAcked) {
                        byte[] sendEndData = formatPacketData(0, FIN.name());
                        System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendEndData));
                        sendMessage(sendEndData, serverIpAddress, sequenceNumber, clientSocket, timerByPacket, dataByPacket);
                    }
                } catch (SocketTimeoutException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static void sendMessage(byte[] sendData, InetAddress serverIpAddress, int sequenceNumber, DatagramSocket clientSocket,
                                    Map<Integer, Long> timerByPacket, Map<Integer, byte[]> dataByPacket) throws IOException {

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverIpAddress, SERVER_PORT);
        clientSocket.send(sendPacket);

        timerByPacket.put(sequenceNumber, System.currentTimeMillis());
        dataByPacket.put(sequenceNumber, sendData);
    }
}