import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class LocalDatabaseInitializer {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String username = "root";
        String password = "";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, username, password);
            Statement stmt = conn.createStatement();

            System.out.println("Connected to local MySQL successfully!");

            System.out.println("Creating database if not exists...");
            stmt.execute("CREATE DATABASE IF NOT EXISTS tools DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci");

            System.out.println("Using database...");
            stmt.execute("USE tools");

            System.out.println("Creating activation_record table...");
            String createTableSql = "CREATE TABLE IF NOT EXISTS activation_record (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "serial_number VARCHAR(512) NOT NULL COMMENT 'serial number', " +
                "activation_code TEXT NOT NULL COMMENT 'activation code', " +
                "expire_time BIGINT NOT NULL COMMENT 'expire timestamp', " +
                "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time', " +
                "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time', " +
                "UNIQUE KEY uk_serial_number (serial_number)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='activation record table'";
            stmt.execute(createTableSql);

            stmt.close();
            conn.close();
            System.out.println("Database initialization completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
