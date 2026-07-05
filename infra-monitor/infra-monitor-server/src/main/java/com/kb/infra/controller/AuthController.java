package com.kb.infra.controller;

import com.kb.common.result.Result;
import com.kb.infra.util.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPasswordHash;

    public AuthController(JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          @Value("${infra.admin.username:admin}") String adminUsername,
                          @Value("${infra.admin.password:admin123}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = passwordEncoder.encode(adminPassword);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!adminUsername.equals(request.getUsername()) ||
                !passwordEncoder.matches(request.getPassword(), adminPasswordHash)) {
            return Result.fail(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(request.getUsername());
        return Result.ok(new LoginResponse(token, request.getUsername()));
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String username;

        public LoginResponse(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }
}
