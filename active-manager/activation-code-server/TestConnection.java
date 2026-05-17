import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class TestConnection {
    public static void main(String[] args) {
        String host = "192.168.31.182";
        int port = 3306;
        String database = "tools";
        String username = "tools";
        String password = "toolsmarschat";

        System.out.println("=== Testing Database Connection with JDK 21 ===\n");

        // Test 1: Simple Socket Connection
        System.out.println("Test 1: Simple Socket Connection");
        try {
            System.out.println("Connecting to " + host + ":" + port);
            Socket socket = new Socket(host, port);
            System.out.println("✓ Socket connection successful!");
            socket.close();
        } catch (Exception e) {
            System.out.println("✗ Socket connection failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();

        // Test 2: MySQL JDBC Connection
        System.out.println("Test 2: MySQL JDBC Connection");
        try {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                         "?useUnicode=true&characterEncoding=utf-8&useSSL=false" +
                         "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";

            System.out.println("Connecting to: " + url);
            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✓ JDBC connection successful!");

            // Test Query
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT 1");
            System.out.println("✓ Query execution successful!");

            stmt.close();
            conn.close();
            System.out.println("✓ Connection closed successfully!");

        } catch (Exception e) {
            System.out.println("✗ JDBC connection failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== All Tests Completed ===");
    }
}
