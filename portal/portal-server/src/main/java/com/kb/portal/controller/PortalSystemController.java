package com.kb.portal.controller;

import com.marschat.auth.authz.RequirePermission;
import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.portal.dto.PortalSystemRequest;
import com.kb.portal.dto.SystemCredentials;
import com.kb.portal.entity.PortalSystem;
import com.kb.portal.service.PortalSystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门户系统条目。
 *
 * <p><b>🔴 2026-09-15 安全修复（P1）：敏感接口补鉴权。</b>
 * 此前本控制器所有端点<b>只有认证（{@code JwtInterceptor}）没有鉴权</b>：任意一个登录用户
 * 都能 {@code GET /api/sys/system/{id}/credentials} 读取任意系统的账号密码明文。
 * 现按「写操作 + 凭据读取」补 api 权限点闸门（随 menu-registry.yml 上报为
 * {@code marschat-portal:api:system:write} / {@code marschat-portal:api:system:credentials}）：
 * 读列表/详情仍对登录用户开放（门户首页要渲染卡片），其余一律走权限点判定，
 * 判定实现见 {@code PortalPermissionChecker}（无权限 403，中心不可达 fail-closed）。
 */
@RestController
@RequestMapping("/api/sys/system")
@RequiredArgsConstructor
public class PortalSystemController {

    private final PortalSystemService portalSystemService;

    @GetMapping("/list")
    public Result<PageResult<PortalSystem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean hasCredentials,
            @RequestParam(required = false) Boolean hasUrl,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(portalSystemService.list(keyword, category, status, hasCredentials, hasUrl, page, size));
    }

    @GetMapping("/all")
    public Result<List<PortalSystem>> all() {
        return Result.ok(portalSystemService.listAllEnabled());
    }

    @GetMapping("/category/{category}")
    public Result<List<PortalSystem>> listByCategory(@PathVariable String category) {
        return Result.ok(portalSystemService.listByCategory(category));
    }

    @GetMapping("/{id}")
    public Result<PortalSystem> getById(@PathVariable Long id) {
        return Result.ok(portalSystemService.getById(id));
    }

    // 🔴 凭据明文读取：必须由中心权限点显式授权，普通用户不得读取
    @RequirePermission("api:system:credentials")
    @GetMapping("/{id}/credentials")
    public Result<SystemCredentials> getCredentials(@PathVariable Long id) {
        return Result.ok(portalSystemService.getCredentials(id));
    }

    @RequirePermission("api:system:write")
    @PostMapping
    public Result<PortalSystem> create(@Valid @RequestBody PortalSystemRequest request) {
        return Result.ok(portalSystemService.create(request));
    }

    @RequirePermission("api:system:write")
    @PutMapping("/{id}")
    public Result<PortalSystem> update(@PathVariable Long id, @Valid @RequestBody PortalSystemRequest request) {
        return Result.ok(portalSystemService.update(id, request));
    }

    @RequirePermission("api:system:write")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        portalSystemService.delete(id);
        return Result.ok();
    }
}
