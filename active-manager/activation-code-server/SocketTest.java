import java.net.Socket;

public class SocketTest {
    public static void main(String[] args) {
        String host = "192.168.31.182";
        int port = 3306;
        
        try {
            System.out.println("Connecting to " + host + ":" + port);
            Socket socket = new Socket(host, port);
            System.out.println("Connected successfully!");
            socket.close();
        } catch (Exception e) {
            System.out.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
