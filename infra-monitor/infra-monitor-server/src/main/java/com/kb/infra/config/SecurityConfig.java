package com.kb.infra.config;

import com.kb.infra.filter.JwtAuthFilter;
import com.marschat.auth.anonymous.AnonymousAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AnonymousAuthProperties anonymousAuth;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AnonymousAuthProperties anonymousAuth) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.anonymousAuth = anonymousAuth;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 🔓 2026-10-15：匿名放行清单从配置读取（marschat.auth.anonymous.paths，
                //    auth-core 2.3.0 的 AnonymousAuthProperties），并与代码内「登录保底集」合并
                //    （保底集在公共库代码内合并、配置不可清空，防误配打断登录链路）；
                //    收放匿名权只改 yml + 重启，不再需要改代码发版。
                //    🧭 路径口径：requestMatchers 按「context-path 剥离后」的路径匹配
                //    （浏览器 /infra/** → nginx → 应用收 /infra/** → 剥 /infra），
                //    故 yml paths 一律写**剥离后**形态，且**不配** anonymous.context-path
                //    （配了只会多生成一份永不命中的 /infra/** 副本，无害但误导）。
                //    ⚠️ infra 的登录端点是 /auth/* 形态（**不带 /api**，见 AuthController），
                //    公共库保底集是 /api/auth/* 形态、覆盖不到 ⇒ 必须在 yml paths 显式声明
                //    （保底集是安全网，不是真相源）。
                //    🔴 /auth/permissions（权限探针）**不在**本清单：保持 anyRequest().authenticated()
                //    保护（见 AuthController#permissions 的安全边界注释）。勿把 "/auth/**" 之类
                //    的粗粒度模式写进 paths——会连探针一起放开。
                .requestMatchers(anonymousAuth.effectivePaths().toArray(new String[0])).permitAll()
                .anyRequest().authenticated()
            )
            // 🔴 2026-09-14 回归修复：未认证（无 token / token 过期 / token 无效）必须 401，
            //    不能落 Spring Security 默认的 Http403ForbiddenEntryPoint（403）——
            //    前端 401 拦截器据此触发静默重授权（renewByReauthorize，IdP 会话在则无感续期）；
            //    返回 403 时拦截器不触发，用户只看到「获取看板数据失败」，token 过期即假死。
            //    语义对齐 HTTP 规范：401=未认证，403=已认证但无权限。
            .exceptionHandling(e -> e.authenticationEntryPoint(
                (request, response, authException) -> response.sendError(
                    jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
