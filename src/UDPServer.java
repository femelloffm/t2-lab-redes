/*----------------------------------------------------------------------------------------*/
/* T2 - Laboratório de Redes de Computadores - Professora Cristina Moreira Nunes - 2023/1 */
/* André Luiz Rodrigues, Fernanda Ferreira de Mello, Matheus Pozzer Moraes                */
/*----------------------------------------------------------------------------------------*/

import enums.CongestionControlType;
import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static config.AppConstants.*;
import static enums.MessageType.*;
import static util.PacketFormatter.formatPacketData;

class UDPServer {

    public static void main(String[] args) throws InterruptedException {

        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {

            int lastValidReceivedSequenceNumber = -1;
            byte[] sendData;
            byte[] receiveData = new byte[300];
            boolean hasActiveConnection = false;
            int ackNumber = 0;
            int connectionNumber = 0;
            int cwnd = 1;
            CongestionControlType currentCongestionControlType = CongestionControlType.SLOW_START;
            int receivedDataCount = 0;
            Map<Integer, byte[]> dataBySequenceNumber = new HashMap<>();
            Map<Integer, Integer> numberOfSendsByAckNumber = new HashMap<>();

            while (true) {
                Thread.sleep(SLEEP_TIME);

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
                    System.out.printf("\n-------------ESTABELECENDO CONEXAO COM CLIENTE %s:%d-------------\n", clientIpAddress.getHostAddress(), clientPort);
                    sendData = formatPacketData(ackNumber, SYNACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                    System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    serverSocket.send(sendPacket);
                    hasActiveConnection = true;
                    connectionNumber += 1;
                }
                // RECEBEU SOLICITACAO PARA ENCERRAMENTO DE CONEXAO
                else if (hasActiveConnection && packetDataParts[3].equals(FIN.name())) {
                    sendData = formatPacketData(0, FINACK.name());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
                    System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    serverSocket.send(sendPacket);

                    // Reseta variaveis
                    hasActiveConnection = false;
                    ackNumber = 0;
                    receivedDataCount = 0;
                    cwnd = 1;
                    currentCongestionControlType = CongestionControlType.SLOW_START;
                    lastValidReceivedSequenceNumber = -1;
                    dataBySequenceNumber.clear();
                    numberOfSendsByAckNumber.clear();
                }
                // RECEBEU DADOS
                else if (hasActiveConnection && !packetDataParts[3].equals(ACK.name())) {
                    final int receivedSequenceNumber = Integer.parseInt(packetDataParts[0]);
                    boolean hasAlreadyUpdatedCwnd = false;

                    // RECEBEU MENSAGEM RESTART INDICANDO VOLTA PARA O INICIO DO SLOW START
                    if (packetDataParts[3].equals(RESTART.name()) && (receivedDataCount < cwnd)) {
                        cwnd = 1;
                        currentCongestionControlType = CongestionControlType.SLOW_START;
                        // Mandar ACK
                        sendAckMessage(ackNumber, clientIpAddress, clientPort, serverSocket, numberOfSendsByAckNumber);
                        continue;
                    }

                    if (cwnd != 1 && numberOfSendsByAckNumber.getOrDefault(receivedSequenceNumber, 0) >= 3) {
                        hasAlreadyUpdatedCwnd = true;
                        cwnd = cwnd / 2;
                    }

                    System.out.printf("Janela de congestionamento: %d   Pacotes ja recebidos da janela: %d\n", cwnd, receivedDataCount);

                    // Se recebeu pacote fora de ordem ou perdeu um pacote
                    if ((receivedSequenceNumber != (lastValidReceivedSequenceNumber + 1))) {
                        System.out.printf("Recebeu pacote fora de ordem. Esperava num. de sequencia %d e recebeu num %d\n",
                                (lastValidReceivedSequenceNumber + 1), receivedSequenceNumber);
                        // Armazena dado que veio fora de ordem
                        dataBySequenceNumber.put(receivedSequenceNumber, packetDataParts[3].getBytes());
                        // Manda ACK com ack number igual a (lastValidReceivedSequenceNumber + 1)
                        sendAckMessage((lastValidReceivedSequenceNumber + 1), clientIpAddress, clientPort, serverSocket, numberOfSendsByAckNumber);
                        receivedDataCount++;
                    }
                    // Se recebeu pacote com numero de sequencia esperado
                    else {
                        receivedDataCount++;
                        ackNumber = receivedSequenceNumber + 1;
                        lastValidReceivedSequenceNumber = receivedSequenceNumber;
                        FileUtil.writeBytesToFile(String.format("copia%d.txt", connectionNumber), packetDataParts[3].getBytes());

                        // Se eu ja tinha recebido os proximos pacotes fora de ordem antes, tambem posso escrever e confirmar eles agora
                        int nextSequenceNumber = lastValidReceivedSequenceNumber + 1;
                        while (true) {
                            if (!dataBySequenceNumber.containsKey(nextSequenceNumber))
                                break;

                            // escrever os dados desse num sequencia no arquivo
                            FileUtil.writeBytesToFile(String.format("copia%d.txt", connectionNumber), dataBySequenceNumber.get(nextSequenceNumber));
                            // incrementar o ackNumber e o lastValidReceivedSequenceNumber em 1
                            ackNumber++;
                            lastValidReceivedSequenceNumber++;
                            //receivedDataCount++;
                            // remover esse num sequencia do map
                            dataBySequenceNumber.remove(nextSequenceNumber);
                            // incrementa chave para proxima iteracao
                            nextSequenceNumber++;
                        }

                        // Se ja recebeu todos os pacotes da janela de congestionamento, manda apenas um ack acumulando todos os acks
                        if (receivedDataCount >= cwnd) {
                            sendAckMessage(ackNumber, clientIpAddress, clientPort, serverSocket, numberOfSendsByAckNumber);
                            receivedDataCount = 0;

                            if (currentCongestionControlType.equals(CongestionControlType.SLOW_START) && cwnd < THRESHOLD && !hasAlreadyUpdatedCwnd) {
                                cwnd = cwnd * 2;
                            } else if (currentCongestionControlType.equals(CongestionControlType.SLOW_START) && !hasAlreadyUpdatedCwnd) {
                                currentCongestionControlType = CongestionControlType.CONGESTION_AVOIDANCE;
                            }

                            if (currentCongestionControlType.equals(CongestionControlType.CONGESTION_AVOIDANCE) && !hasAlreadyUpdatedCwnd) {
                                cwnd += 1;
                            }
                        }
                        // Se podia receber mais pacotes na janela, mas ja recebeu o ultimo pacote com os dados do arquivo, envia um ack acumulando os acks
                        // Se recebi um pacote com dados e padding no final, é o ultimo pacote
                        else if (packetDataParts.length == 5) {
                            sendAckMessage(ackNumber, clientIpAddress, clientPort, serverSocket, numberOfSendsByAckNumber);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendAckMessage(int ackNumber, InetAddress clientIpAddress, int clientPort, DatagramSocket serverSocket,
                                       Map<Integer, Integer> numberOfSendsByAckNumber) throws IOException {
        byte[] sendData = formatPacketData(ackNumber, ACK.name());
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIpAddress, clientPort);
        System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
        serverSocket.send(sendPacket);
        numberOfSendsByAckNumber.put(ackNumber, numberOfSendsByAckNumber.getOrDefault(ackNumber, 0) + 1);
    }
}