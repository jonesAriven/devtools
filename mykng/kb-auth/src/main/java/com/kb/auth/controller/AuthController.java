package com.kb.auth.controller;

import com.kb.auth.dto.LoginRequest;
import com.kb.auth.dto.LoginResponse;
import com.kb.auth.dto.RefreshRequest;
import com.kb.auth.entity.User;
import com.kb.auth.service.AuthService;
import com.kb.auth.service.UserService;
import com.kb.auth.util.SecurityUtils;
import com.kb.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        authService.logout(token);
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    @GetMapping("/me")
    public Result<LoginResponse> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userService.getProfile(userId);
        String accessToken = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        return Result.ok(new LoginResponse(accessToken, null, 3600000L, user));
    }
}
