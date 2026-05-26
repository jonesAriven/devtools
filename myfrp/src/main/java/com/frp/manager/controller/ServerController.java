package com.frp.manager.controller;

import com.frp.manager.dto.ApiResponse;
import com.frp.manager.entity.FrpServer;
import com.frp.manager.entity.FrpTunnel;
import com.frp.manager.service.FrpServerService;
import com.frp.manager.entity.FrpClient;
import com.frp.manager.service.FrpClientService;
import com.frp.manager.service.FrpTunnelService;
import com.frp.manager.util.ConfigGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/server")
@RequiredArgsConstructor
public class ServerController {

    private final FrpServerService serverService;
    private final FrpClientService clientService;
    private final FrpTunnelService tunnelService;
    private final ConfigGenerator configGenerator;

    @GetMapping("/list")
    public ApiResponse<List<FrpServer>> list() {
        return ApiResponse.success(serverService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<FrpServer> getById(@PathVariable Long id) {
        FrpServer server = serverService.getById(id);
        if (server == null) return ApiResponse.error(404, "服务端不存在");
        return ApiResponse.success(server);
    }

    @PostMapping
    public ApiResponse<FrpServer> create(@RequestBody FrpServer server) {
        serverService.save(server);
        return ApiResponse.success(server);
    }

    @PutMapping("/{id}")
    public ApiResponse<FrpServer> update(@PathVariable Long id, @RequestBody FrpServer server) {
        server.setId(id);
        serverService.updateById(server);
        return ApiResponse.success(serverService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        serverService.removeById(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/deploy")
    public ApiResponse<Map<String, Object>> deploy(@PathVariable Long id) {
        FrpServer server = serverService.getById(id);
        if (server == null) return ApiResponse.error(404, "服务端不存在");

        // 获取该服务端下所有隧道
        List<FrpTunnel> tunnels = getTunnelsByServerId(id);
        String config = configGenerator.generateFrpsIni(server, tunnels);

        Map<String, Object> result = new HashMap<>();
        result.put("config", config);
        result.put("message", "配置生成成功（SSH部署功能待对接）");
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/restart")
    public ApiResponse<Map<String, Object>> restart(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "重启命令已发送（SSH部署功能待对接）");
        result.put("status", "pending");
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long id) {
        FrpServer server = serverService.getById(id);
        if (server == null) return ApiResponse.error(404, "服务端不存在");

        Map<String, Object> status = new HashMap<>();
        status.put("id", server.getId());
        status.put("name", server.getName());
        status.put("host", server.getHost());
        status.put("online", server.getStatus() == 1);
        status.put("running", server.getStatus() == 1);
        return ApiResponse.success(status);
    }

    private List<FrpTunnel> getTunnelsByServerId(Long serverId) {
        List<FrpClient> clients = clientService.lambdaQuery()
                .eq(FrpClient::getServerId, serverId).list();
        if (clients.isEmpty()) return Collections.emptyList();
        List<Long> clientIds = clients.stream().map(FrpClient::getId).toList();
        return tunnelService.lambdaQuery().in(FrpTunnel::getClientId, clientIds).list();
    }
}
