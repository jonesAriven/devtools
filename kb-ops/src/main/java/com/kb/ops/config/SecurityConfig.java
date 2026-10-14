package com.kb.ops.config;

import com.kb.ops.security.JwtAuthenticationFilter;
import com.marschat.auth.anonymous.AnonymousAuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AnonymousAuthProperties anonymousAuth;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          AnonymousAuthProperties anonymousAuth) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.anonymousAuth = anonymousAuth;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 🔓 2026-10-15：匿名放行清单从配置读取（marschat.auth.anonymous.paths，
                //    auth-core 2.3.0 的 AnonymousAuthProperties），并与代码内「登录保底集」合并
                //    （保底集在公共库代码内合并、配置不可清空，防误配打断登录链路）；
                //    收放匿名权只改 yml + 重启，不再需要改代码发版。
                //    🧭 路径口径：requestMatchers 按「context-path 剥离后」的路径匹配
                //    （前端 /ops-api/** → nginx 改写加 /kb-ops → 应用收 /kb-ops/** → 剥 /kb-ops），
                //    故 yml paths 一律写**剥离后**形态，且**不配** anonymous.context-path
                //    （配了只会多生成一份永不命中的 /kb-ops/** 副本，无害但误导）。
                .requestMatchers(anonymousAuth.effectivePaths().toArray(new String[0])).permitAll()
                // CORS 预检与登录校验无关（传输层放行），保持硬编码
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            /**
             * 🔴 显式 401 entry point（坑 #2，Phase 13 迁移时补上）。
             *
             * 不配时 Spring Security 默认走 `Http403ForbiddenEntryPoint` —— **未携带 token**
             * 的请求返回 403，而 `JwtAuthenticationFilter` 对**无效 token** 返回 401，
             * 同一语义两种状态码。前端 401 拦截器只认 401，遇到 403 既不续期也不跳登录
             * → 页面「假死」（点了没反应、也不提示）。
             *
             * 响应体格式与 filter 的 `sendUnauthorized` 保持一致，前端按 Result 信封解析。
             */
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
            }))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
