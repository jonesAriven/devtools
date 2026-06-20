package com.kb.ops.feign;

import com.kb.common.result.Result;
import com.kb.ops.feign.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * kb-auth Feign 客户端
 * <p>
 * 用于跨服务验证用户身份、获取用户信息。
 * kb-ops → kb-auth 允许调用（架构依赖规则）。
 */
@FeignClient(name = "kb-auth", url = "${kb.feign.auth-url:http://kb-auth:8081}")
public interface AuthClient {

    /**
     * 根据用户 ID 获取用户信息（含状态）
     * 调用 kb-auth 的内部接口（需 kb-auth 暴露 /api/user/internal/{id} 端点）
     */
    @GetMapping("/user/internal/{id}")
    Result<UserDTO> getUserById(@PathVariable("id") Long id);

    /**
     * 验证用户状态是否正常（status=1）
     */
    default boolean isUserActive(Long userId) {
        try {
            Result<UserDTO> result = getUserById(userId);
            if (result != null && result.getCode() == 200 && result.getData() != null) {
                return result.getData().getStatus() != null && result.getData().getStatus() == 1;
            }
        } catch (Exception e) {
            // Feign 调用失败时默认放行，避免下游服务故障影响主流程
        }
        return true;
    }
}
