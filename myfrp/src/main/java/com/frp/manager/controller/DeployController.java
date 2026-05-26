package com.frp.manager.controller;

import com.frp.manager.dto.ApiResponse;
import com.frp.manager.entity.FrpClient;
import com.frp.manager.entity.FrpServer;
import com.frp.manager.entity.FrpTunnel;
import com.frp.manager.service.FrpClientService;
import com.frp.manager.service.FrpServerService;
import com.frp.manager.service.FrpTunnelService;
import com.frp.manager.util.ConfigGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/deploy")
@RequiredArgsConstructor
public class DeployController {

    private final FrpServerService serverService;
    private final FrpClientService clientService;
    private final FrpTunnelService tunnelService;
    private final ConfigGenerator configGenerator;

    @PostMapping("/frps/{serverId}")
    public ApiResponse<Map<String, Object>> deployFrps(@PathVariable Long serverId) {
        FrpServer server = serverService.getById(serverId);
        if (server == null) return ApiResponse.error(404, "服务端不存在");

        List<FrpTunnel> tunnels = getTunnelsByServerId(serverId);
        String config = configGenerator.generateFrpsIni(server, tunnels);
        Map<String, Object> result = new HashMap<>();
        result.put("config", config);
        result.put("message", "配置生成成功");
        return ApiResponse.success(result);
    }

    @PostMapping("/frpc/{clientId}")
    public ApiResponse<Map<String, Object>> deployFrpc(@PathVariable Long clientId) {
        FrpClient client = clientService.getById(clientId);
        if (client == null) return ApiResponse.error(404, "客户端不存在");

        FrpServer server = serverService.getById(client.getServerId());
        List<FrpTunnel> tunnels = tunnelService.lambdaQuery()
                .eq(FrpTunnel::getClientId, clientId).list();
        String config = configGenerator.generateConfig(client, server, tunnels);

        Map<String, Object> result = new HashMap<>();
        result.put("config", config);
        result.put("format", client.getConfigFormat());
        result.put("message", "配置生成成功");
        return ApiResponse.success(result);
    }

    @GetMapping("/preview/frps/{serverId}")
    public ApiResponse<Map<String, Object>> previewFrps(@PathVariable Long serverId) {
        FrpServer server = serverService.getById(serverId);
        if (server == null) return ApiResponse.error(404, "服务端不存在");

        List<FrpTunnel> tunnels = getTunnelsByServerId(serverId);
        String config = configGenerator.generateFrpsIni(server, tunnels);
        return ApiResponse.success(Map.of("config", config, "format", "ini"));
    }

    @GetMapping("/preview/frpc/{clientId}")
    public ApiResponse<Map<String, Object>> previewFrpc(@PathVariable Long clientId) {
        FrpClient client = clientService.getById(clientId);
        if (client == null) return ApiResponse.error(404, "客户端不存在");

        FrpServer server = serverService.getById(client.getServerId());
        List<FrpTunnel> tunnels = tunnelService.lambdaQuery()
                .eq(FrpTunnel::getClientId, clientId).list();
        String config = configGenerator.generateConfig(client, server, tunnels);
        return ApiResponse.success(Map.of("config", config, "format", client.getConfigFormat()));
    }

    @PostMapping("/all")
    public ApiResponse<List<Map<String, Object>>> deployAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        List<FrpServer> servers = serverService.list();
        for (FrpServer server : servers) {
            List<FrpTunnel> tunnels = getTunnelsByServerId(server.getId());
            String config = configGenerator.generateFrpsIni(server, tunnels);
            results.add(Map.of(
                    "type", "server", "name", server.getName(),
                    "config", config, "status", "generated"
            ));
        }
        List<FrpClient> clients = clientService.list();
        for (FrpClient client : clients) {
            FrpServer server = serverService.getById(client.getServerId());
            List<FrpTunnel> tunnels = tunnelService.lambdaQuery()
                    .eq(FrpTunnel::getClientId, client.getId()).list();
            String config = configGenerator.generateConfig(client, server, tunnels);
            results.add(Map.of(
                    "type", "client", "name", client.getName(),
                    "config", config, "status", "generated"
            ));
        }
        return ApiResponse.success(results);
    }

    private List<FrpTunnel> getTunnelsByServerId(Long serverId) {
        List<FrpClient> clients = clientService.lambdaQuery()
                .eq(FrpClient::getServerId, serverId).list();
        if (clients.isEmpty()) return Collections.emptyList();
        List<Long> clientIds = clients.stream().map(FrpClient::getId).toList();
        return tunnelService.lambdaQuery().in(FrpTunnel::getClientId, clientIds).list();
    }
}
