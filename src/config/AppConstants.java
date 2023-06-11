/*----------------------------------------------------------------------------------------*/
/* T2 - Laboratório de Redes de Computadores - Professora Cristina Moreira Nunes - 2023/1 */
/* André Luiz Rodrigues, Fernanda Ferreira de Mello, Matheus Pozzer Moraes                */
/*----------------------------------------------------------------------------------------*/
package config;

public class AppConstants {
    public final static char SEPARATOR = '/';
    public final static Integer PACKET_BYTE_SIZE = 300;
    public final static Integer HEADERS_BYTE_SIZE = 20;
    public final static Integer MAX_DATA_BYTE_SIZE = PACKET_BYTE_SIZE - HEADERS_BYTE_SIZE;
    public final static Integer SERVER_PORT = 9876;
    public final static Integer ACK_TIMEOUT = 30000;
    public final static Integer SLEEP_TIME = 500;
    public final static Integer THRESHOLD = 8;
    public final static Integer SOCKET_TIMEOUT = 60000;
}
