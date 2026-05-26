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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientController {

    private final FrpClientService clientService;
    private final FrpServerService serverService;
    private final FrpTunnelService tunnelService;
    private final ConfigGenerator configGenerator;

    @GetMapping("/list")
    public ApiResponse<List<FrpClient>> list(@RequestParam(required = false) Long serverId) {
        if (serverId != null) {
            return ApiResponse.success(clientService.lambdaQuery().eq(FrpClient::getServerId, serverId).list());
        }
        return ApiResponse.success(clientService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<FrpClient> getById(@PathVariable Long id) {
        FrpClient client = clientService.getById(id);
        if (client == null) return ApiResponse.error(404, "客户端不存在");
        return ApiResponse.success(client);
    }

    @PostMapping
    public ApiResponse<FrpClient> create(@RequestBody FrpClient client) {
        clientService.save(client);
        return ApiResponse.success(client);
    }

    @PutMapping("/{id}")
    public ApiResponse<FrpClient> update(@PathVariable Long id, @RequestBody FrpClient client) {
        client.setId(id);
        clientService.updateById(client);
        return ApiResponse.success(clientService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        clientService.removeById(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/deploy")
    public ApiResponse<Map<String, Object>> deploy(@PathVariable Long id) {
        FrpClient client = clientService.getById(id);
        if (client == null) return ApiResponse.error(404, "客户端不存在");

        FrpServer server = serverService.getById(client.getServerId());
        List<FrpTunnel> tunnels = tunnelService.lambdaQuery()
                .eq(FrpTunnel::getClientId, id)
                .list();

        String config = configGenerator.generateConfig(client, server, tunnels);

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
        FrpClient client = clientService.getById(id);
        if (client == null) return ApiResponse.error(404, "客户端不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("id", client.getId());
        result.put("name", client.getName());
        result.put("host", client.getHost());
        result.put("online", client.getStatus() == 1);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/test-ssh")
    public ApiResponse<Map<String, Object>> testSsh(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "SSH测试功能待对接");
        result.put("reachable", false);
        return ApiResponse.success(result);
    }
}
