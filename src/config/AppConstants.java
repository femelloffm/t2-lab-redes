package config;

public class AppConstants {
    public final static char SEPARATOR = '/';
    public final static Integer PACKET_BYTE_SIZE = 300;
    public final static Integer HEADERS_BYTE_SIZE = 20;
    public final static Integer MAX_DATA_BYTE_SIZE = PACKET_BYTE_SIZE - HEADERS_BYTE_SIZE;
    public final static Integer SERVER_PORT = 9876;
    public final static Integer ACK_TIMEOUT = 30000;
}
