import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseInitializer {
    public static void main(String[] args) {
        String url = "jdbc:mysql://192.168.31.182:3306?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String username = "tools";
        String password = "toolsmarschat";

        String[] sqlStatements = {
            "CREATE DATABASE IF NOT EXISTS tools DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci",
            "USE tools",
            "CREATE TABLE IF NOT EXISTS activation_record (" +
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "    serial_number VARCHAR(512) NOT NULL COMMENT '唯一序列号'," +
            "    activation_code TEXT NOT NULL COMMENT '激活码'," +
            "    expire_time BIGINT NOT NULL COMMENT '过期时间戳(毫秒)'," +
            "    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
            "    UNIQUE KEY uk_serial_number (serial_number)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激活码记录表'"
        };

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url, username, password);
            Statement stmt = conn.createStatement();

            for (String sql : sqlStatements) {
                System.out.println("Executing: " + sql);
                stmt.execute(sql);
            }

            stmt.close();
            conn.close();
            System.out.println("Database initialization completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
