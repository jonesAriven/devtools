package com.frp.manager.controller;

import com.frp.manager.dto.ApiResponse;
import com.frp.manager.entity.FrpTunnel;
import com.frp.manager.service.FrpTunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tunnel")
@RequiredArgsConstructor
public class TunnelController {

    private final FrpTunnelService tunnelService;

    @GetMapping("/list")
    public ApiResponse<List<FrpTunnel>> list(@RequestParam(required = false) Long clientId) {
        if (clientId != null) {
            return ApiResponse.success(tunnelService.lambdaQuery()
                    .eq(FrpTunnel::getClientId, clientId)
                    .list());
        }
        return ApiResponse.success(tunnelService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<FrpTunnel> getById(@PathVariable Long id) {
        FrpTunnel tunnel = tunnelService.getById(id);
        if (tunnel == null) return ApiResponse.error(404, "隧道不存在");
        return ApiResponse.success(tunnel);
    }

    @PostMapping
    public ApiResponse<FrpTunnel> create(@RequestBody FrpTunnel tunnel) {
        tunnelService.save(tunnel);
        return ApiResponse.success(tunnel);
    }

    @PutMapping("/{id}")
    public ApiResponse<FrpTunnel> update(@PathVariable Long id, @RequestBody FrpTunnel tunnel) {
        tunnel.setId(id);
        tunnelService.updateById(tunnel);
        return ApiResponse.success(tunnelService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tunnelService.removeById(id);
        return ApiResponse.success(null);
    }
}
