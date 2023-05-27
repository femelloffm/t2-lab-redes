// Recebe um pacote de algum cliente
// Separa o dado, o endere�o IP e a porta deste cliente
// Imprime o dado na tela

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.zip.CRC32;

class UDPServer {
    private final static String SEPARATOR = "/";
    private final static CRC32 crcCalculator = new CRC32();

    public static void main(String args[]) throws Exception {
        // cria socket do servidor com a porta 9876
        DatagramSocket serverSocket = new DatagramSocket(9876);

        byte[] receiveData = new byte[1024];
        byte[] sendData = new byte[1024];
        boolean hasActiveConnection = false;
        while (true) {
            // declara o pacote a ser recebido
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            // recebe o pacote do cliente
            serverSocket.receive(receivePacket);

            while (true) {
                // pega os dados, o endere�o IP e a porta do cliente
                // para poder mandar a msg de volta
                String sentence = new String(receivePacket.getData());
                InetAddress IPAddress = receivePacket.getAddress();
                int port = receivePacket.getPort();
                System.out.println("Mensagem recebida: " + sentence);

                String[] sentenceParts = sentence.split(SEPARATOR);

                crcCalculator.update(sentenceParts[2].getBytes());
                long checkCalculatedCrc = crcCalculator.getValue();
                if (checkCalculatedCrc != Long.valueOf(sentenceParts[1])) {
                    break;
                }

                // falta no cliente e servidor calcular e validar o CRC para cada mensagem
                // recebida
                if (sentenceParts[2].contains("SYN")) {
                    System.out.println("SOLICITACAO DE ESTABELECIMENTO DE CONEXAO RECEBIDA");
                    // recebeu mensagem com num sequencia 0 --> primeiro pacote
                    String data = "SYNACK";
                    crcCalculator.update(data.getBytes());
                    long calculatedCrc = crcCalculator.getValue();
                    String sendStr = String.format("0%s%d%s%s", SEPARATOR, calculatedCrc, SEPARATOR, data);
                    sendData = sendStr.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);

                    System.out.println("ENVIANDO SYN + ACK PARA ESTABELECIMENTO DE CONEXAO");
                    serverSocket.send(sendPacket);

                    crcCalculator.reset();
                    Arrays.fill(receiveData, (byte) 0);
                    Arrays.fill(sendData, (byte) 0);
                }

                break;
            }
            break;

        }
    }
}