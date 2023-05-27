// L� uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import java.io.*; // classes para input e output streams e
import java.net.*;// DatagramaSocket,InetAddress,DatagramaPacket
import java.util.zip.CRC32;

class UDPClient {
   private final static String SEPARATOR = "/";
   private final static CRC32 crcCalculator = new CRC32();

   public static void main(String args[]) throws Exception {
      // cria o stream do teclado
      BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

      // declara socket cliente
      DatagramSocket clientSocket = new DatagramSocket();

      // obtem endere�o IP do servidor com o DNS
      InetAddress IPAddress = InetAddress.getByName("localhost");

      byte[] sendData = new byte[1024];
      byte[] receiveData = new byte[1024];

      // l� uma linha do teclado
      String sentenceData = inFromUser.readLine();
      crcCalculator.update(sentenceData.getBytes());
      long calculatedCrc = crcCalculator.getValue();
      String sentence = String.format("0%s%d%s%s", SEPARATOR, calculatedCrc, SEPARATOR, sentenceData);
      sendData = sentence.getBytes();

      // ESTABELECIMENTO DE CONEXÃO

      // cria pacote com o dado, o endere�o do server e porta do servidor
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);

      // envia o pacote
      clientSocket.send(sendPacket);

      DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

      clientSocket.receive(receivePacket);

      String receivedSentence = new String(receivePacket.getData());
      System.out.println("Mensagem recebida: " + receivedSentence);

      if (receivedSentence.contains(receivedSentence))

         // fecha o cliente
         clientSocket.close();
   }
}