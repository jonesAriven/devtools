package com.frp.manager.util;

import com.frp.manager.entity.FrpClient;
import com.frp.manager.entity.FrpServer;
import com.frp.manager.entity.FrpTunnel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class ConfigGenerator {

    /**
     * 生成 frps.ini
     */
    public String generateFrpsIni(FrpServer server, List<FrpTunnel> allTunnels) {
        StringBuilder sb = new StringBuilder();
        sb.append("[common]\n");
        sb.append("bind_port = ").append(server.getBindPort() != null ? server.getBindPort() : 7000).append("\n");
        if (server.getToken() != null && !server.getToken().isEmpty()) {
            sb.append("token = ").append(server.getToken()).append("\n");
        }
        if (server.getDashboardPort() != null) {
            sb.append("dashboard_port = ").append(server.getDashboardPort()).append("\n");
        }
        if (server.getDashboardUser() != null && !server.getDashboardUser().isEmpty()) {
            sb.append("dashboard_user = ").append(server.getDashboardUser()).append("\n");
        }
        if (server.getDashboardPwd() != null && !server.getDashboardPwd().isEmpty()) {
            sb.append("dashboard_pwd = ").append(server.getDashboardPwd()).append("\n");
        }
        if (server.getVhostHttpPort() != null) {
            sb.append("vhost_http_port = ").append(server.getVhostHttpPort()).append("\n");
        }

        // 收集所有远程端口生成 allow_ports
        if (allTunnels != null && !allTunnels.isEmpty()) {
            Set<Integer> ports = allTunnels.stream()
                    .filter(t -> t.getRemotePort() != null)
                    .map(FrpTunnel::getRemotePort)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!ports.isEmpty()) {
                sb.append("allow_ports = ").append(
                        ports.stream().map(String::valueOf).collect(Collectors.joining(", "))
                ).append("\n");
            }
        }

        sb.append("log_file = /var/log/frps/frps.log\n");
        sb.append("log_level = info\n");
        return sb.toString();
    }

    /**
     * 生成 frpc.toml (新版 >= 0.52.0)
     */
    public String generateFrpcToml(FrpClient client, FrpServer server, List<FrpTunnel> tunnels) {
        StringBuilder sb = new StringBuilder();
        if (server != null) {
            sb.append("serverAddr = \"").append(server.getHost()).append("\"\n");
            sb.append("serverPort = ").append(server.getBindPort() != null ? server.getBindPort() : 7000).append("\n");
            if (server.getToken() != null && !server.getToken().isEmpty()) {
                sb.append("auth.token = \"").append(server.getToken()).append("\"\n");
            }
        }
        sb.append("\n");

        if (tunnels != null) {
            for (FrpTunnel tunnel : tunnels) {
                if (tunnel.getStatus() == 0) continue;
                sb.append("[[proxies]]\n");
                sb.append("name = \"").append(tunnel.getName()).append("\"\n");
                sb.append("type = \"").append(tunnel.getType()).append("\"\n");
                sb.append("localIP = \"").append(tunnel.getLocalIp() != null ? tunnel.getLocalIp() : "127.0.0.1").append("\"\n");
                sb.append("localPort = ").append(tunnel.getLocalPort()).append("\n");
                sb.append("remotePort = ").append(tunnel.getRemotePort()).append("\n");
                if (tunnel.getUseEncryption() != null && tunnel.getUseEncryption() == 1) {
                    sb.append("transport.useEncryption = true\n");
                }
                if (tunnel.getUseCompression() != null && tunnel.getUseCompression() == 1) {
                    sb.append("transport.useCompression = true\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 生成 frpc.ini (旧版兼容)
     */
    public String generateFrpcIni(FrpClient client, FrpServer server, List<FrpTunnel> tunnels) {
        StringBuilder sb = new StringBuilder();
        sb.append("[common]\n");
        if (server != null) {
            sb.append("server_addr = ").append(server.getHost()).append("\n");
            sb.append("server_port = ").append(server.getBindPort() != null ? server.getBindPort() : 7000).append("\n");
            if (server.getToken() != null && !server.getToken().isEmpty()) {
                sb.append("token = ").append(server.getToken()).append("\n");
            }
        }
        sb.append("\n");

        if (tunnels != null) {
            for (FrpTunnel tunnel : tunnels) {
                if (tunnel.getStatus() == 0) continue;
                sb.append("[\"").append(tunnel.getName()).append("\"]\n");
                sb.append("type = ").append(tunnel.getType()).append("\n");
                sb.append("local_ip = ").append(tunnel.getLocalIp() != null ? tunnel.getLocalIp() : "127.0.0.1").append("\n");
                sb.append("local_port = ").append(tunnel.getLocalPort()).append("\n");
                sb.append("remote_port = ").append(tunnel.getRemotePort()).append("\n");
                if (tunnel.getUseEncryption() != null && tunnel.getUseEncryption() == 1) {
                    sb.append("use_encryption = true\n");
                }
                if (tunnel.getUseCompression() != null && tunnel.getUseCompression() == 1) {
                    sb.append("use_compression = true\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 根据客户端 config_format 自动选择生成格式
     */
    public String generateConfig(FrpClient client, FrpServer server, List<FrpTunnel> tunnels) {
        if ("ini".equalsIgnoreCase(client.getConfigFormat())) {
            return generateFrpcIni(client, server, tunnels);
        }
        return generateFrpcToml(client, server, tunnels);
    }
}
