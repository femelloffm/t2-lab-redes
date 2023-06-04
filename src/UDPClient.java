// L� uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import dto.FilePacketInformation;
import util.CrcCalculator;
import util.FileUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static enums.MessageType.*;

class UDPClient {
    private final static char SEPARATOR = '/';
    private final static Integer PACKET_BYTE_SIZE = 300;
    private final static Integer HEADERS_BYTE_SIZE = 20;
    private final static Integer MAX_DATA_BYTE_SIZE = PACKET_BYTE_SIZE - HEADERS_BYTE_SIZE;
    private final static Integer PORT = 9876;
    private final static Integer ACK_TIMEOUT = 30000;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("ERRO: deve informar o nome do arquivo e o endereço IP do servidor, nessa ordem, como argumento");
            System.exit(1);
        }

        // Pega bytes do arquivo a enviar
        byte[] fileBytes = FileUtil.readBytesFromFile(args[0]);
        System.out.println("Going to send file " + args[0] + ". File has " + fileBytes.length + " bytes");
        String serverAddress = args[1];
        Map<Integer, Long> timerByPacket = new HashMap<>();
        Map<Integer, byte[]> dataByPacket = new HashMap<>();

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

            System.out.printf("Enviando mensagem para endereco %s:%d --> %s\n", ipServerAddress.getHostAddress(), PORT, new String(sendData));
            clientSocket.send(sendPacket);
            timerByPacket.put(0, System.currentTimeMillis());
            dataByPacket.put(0, sendData);

            while (true) {
                if (timerByPacket.size() > 0) {
                    System.out.println("Horario de envio (ms) de pacotes aguardando ACK:");
                    timerByPacket.forEach((sentSequenceNumber, sendTime) -> {
                        System.out.printf("Num Seq.: %d - Horario: %d\n", sentSequenceNumber, sendTime);
                        // Se passou do timeout, deve retransmitir
                        if ((System.currentTimeMillis() - sendTime) >= ACK_TIMEOUT) {
                            System.out.println("\tTimeout de pacote " + sentSequenceNumber + " alcancado");
                            byte[] bytesToRetransmit = dataByPacket.get(sentSequenceNumber);
                            DatagramPacket retransmitPacket = new DatagramPacket(bytesToRetransmit, bytesToRetransmit.length, ipServerAddress, PORT);
                            System.out.printf("\tRetransmitindo mensagem para servidor %s:%d --> %s\n", ipServerAddress.getHostAddress(), PORT, new String(bytesToRetransmit));
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
                System.out.printf("Mensagem recebida de servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, receivedMessage);

                // VERIFICA CRC
                String[] packetDataParts = receivedMessage.split(String.valueOf(SEPARATOR));
                boolean correctCrc = CrcCalculator.checkReceivedPacketCrc(Long.parseLong(packetDataParts[1]), packetDataParts[3].getBytes());
                if (!correctCrc) {
                    System.out.println("CRC calculado difere do CRC esperado. Descartando pacote...");
                    continue;
                }

                // RECEBEU SOLICITACAO PARA ESTABELECIMENTO DE CONEXAO
                if (!hasActiveConnection && packetDataParts[3].equals(SYNACK.name())) {
                    sendData = formatPacketData(sequenceNumber, ACK.name());
                    sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);
                    System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
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
                    sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);
                    System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
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
                    System.out.println("Recebeu ACK. Parando timer de pacote enviado com num de sequencia " + (ackNumber - 1) + "...");
                }

                // ENVIA DADOS
                if (hasActiveConnection && fileOffset < fileBytes.length) {
                    FilePacketInformation newPacketInfo = formatPacketDataForFileTransfer(sequenceNumber, fileBytes, fileOffset);
                    sendPacket = new DatagramPacket(newPacketInfo.getData(), newPacketInfo.getData().length, ipServerAddress, PORT);
                    System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(newPacketInfo.getData()));
                    clientSocket.send(sendPacket);
                    fileOffset = newPacketInfo.getFileOffset();
                    timerByPacket.put(sequenceNumber, System.currentTimeMillis());
                    dataByPacket.put(sequenceNumber, newPacketInfo.getData());
                    sequenceNumber += 1;

                    // TODO VALIDA ACK PARA ENVIAR DADOS APENAS APOS CONFIRMAR O PROXIMO, VALIDANDO NUMERO DE ACK
                    // TODO CONTROLE DE CONGESTIONAMENTO
                }
                // DEVE ENCERRAR A CONEXAO --> ENVIOU TODOS OS DADOS
                else if (hasActiveConnection && fileOffset >= fileBytes.length) {
                    sendData = formatPacketData(0, FIN.name());
                    sendPacket = new DatagramPacket(sendData, sendData.length, ipServerAddress, PORT);
                    System.out.printf("Enviando mensagem para servidor %s:%d --> %s\n", clientIpAddress.getHostAddress(), clientPort, new String(sendData));
                    clientSocket.send(sendPacket);
                    timerByPacket.put(sequenceNumber, System.currentTimeMillis());
                    dataByPacket.put(sequenceNumber, sendData);
                }
            }
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

    private static FilePacketInformation formatPacketDataForFileTransfer(int sequenceNumber, byte[] fileData, int fileOffset) {
        byte[] dataBytes;
        byte[] dataBytesWithPadding;
        int newOffset;

        int remainingFileDataSize = (fileData.length - fileOffset);
        // Se precisar enviar mensagem com padding (a ultima mensagem com dados do arquivo)
        if (remainingFileDataSize < MAX_DATA_BYTE_SIZE) {
            int tempMaxSize = fileOffset + MAX_DATA_BYTE_SIZE;
            dataBytes = Arrays.copyOfRange(fileData, fileOffset, fileData.length); // Bytes restantes do arquivo
            dataBytesWithPadding = Arrays.copyOfRange(fileData, fileOffset, tempMaxSize); // Bytes restantes do arquivo com excesso preenchido com zeros
            dataBytesWithPadding[remainingFileDataSize] = (byte) SEPARATOR; // Adiciona separador entre dados e padding
            newOffset = fileData.length; // Atualiza offset para final de buffer do arquivo
        } else {
            newOffset = fileOffset + MAX_DATA_BYTE_SIZE;
            dataBytes = Arrays.copyOfRange(fileData, fileOffset, newOffset); // Copia proximo chunk do arquivo
            dataBytesWithPadding = dataBytes;
        }

        // Calcula CRC
        long calculatedCrc = CrcCalculator.calculateCrc(dataBytes);

        // Copia headers para array de HEADERS_BYTE_SIZE (headers devem sempre ter o mesmo tamanho fixo)
        String headerStr = String.format("%d%c%d%c", sequenceNumber, SEPARATOR, calculatedCrc, SEPARATOR);
        byte[] headerBytes = new byte[HEADERS_BYTE_SIZE];
        byte[] headerStrAsBytes = headerStr.getBytes();
        for (int i = 0; i < headerBytes.length; i++) {
            if (headerStrAsBytes.length > i) {
                headerBytes[i] = headerStrAsBytes[i];
            } else {
                break;
            }
        }

        // Add separator between crc and headers padding
        if (headerStrAsBytes.length < headerBytes.length) {
            headerBytes[headerBytes.length - 1] = (byte) SEPARATOR;
        }

        // Build packet from header and data byte arrays
        byte[] finalPacketBytes = new byte[headerBytes.length + dataBytesWithPadding.length];
        ByteBuffer packetBuffer = ByteBuffer.wrap(finalPacketBytes);
        packetBuffer.put(headerBytes);
        packetBuffer.put(dataBytesWithPadding);

        return new FilePacketInformation(packetBuffer.array(), newOffset);
    }
}