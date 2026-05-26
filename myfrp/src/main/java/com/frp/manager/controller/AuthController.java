package com.frp.manager.controller;

import com.frp.manager.dto.ApiResponse;
import com.frp.manager.dto.LoginRequest;
import com.frp.manager.dto.LoginResponse;
import com.frp.manager.entity.SysUser;
import com.frp.manager.security.JwtTokenProvider;
import com.frp.manager.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SysUser user = sysUserService.findByUsername(request.getUsername());
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setRole(user.getRole());
        return ApiResponse.success(response);
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        SysUser user = sysUserService.findByUsername(username);
        Map<String, Object> data = Map.of(
                "username", user.getUsername(),
                "realName", user.getRealName(),
                "role", user.getRole()
        );
        return ApiResponse.success(data);
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody LoginRequest request) {
        SysUser existing = sysUserService.findByUsername(request.getUsername());
        if (existing != null) {
            return ApiResponse.error(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getUsername());
        user.setRole("USER");
        user.setStatus(1);
        sysUserService.save(user);
        return ApiResponse.success(null);
    }
}
