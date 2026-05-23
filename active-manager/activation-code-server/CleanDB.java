import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class CleanDB {
    public static void main(String[] args) {
        String url = "jdbc:mysql://192.168.31.182:3306/tools?useSSL=false&allowPublicKeyRetrieval=true";
        String user = "tools";
        String password = "toolsmarschat";
        String sql = "DELETE FROM activation_record WHERE serial_number LIKE 'QRCodeTool%'";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int rows = ps.executeUpdate();
                System.out.println("删除完成，共删除 " + rows + " 条记录");
            }
        } catch (Exception e) {
            System.err.println("执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
