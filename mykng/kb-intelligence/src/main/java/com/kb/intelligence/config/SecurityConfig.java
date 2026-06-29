package com.kb.intelligence.config;

import com.kb.intelligence.security.GatewayAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * kb-intelligence 安全配置
 * <p>
 * 架构说明：本服务部署在 kb-gateway 之后，网关已通过 JwtAuthFilter 完成 JWT 验证并注入
 * X-User-Id / X-Username 头。下游服务无需重复鉴权，统一 permitAll 放行，由
 * GatewayAuthFilter 解析网关注入的用户身份供业务层使用。
 * <p>
 * 修复历史：
 * - 2026-06-30 修复 requestMatchers("/intelligence/machine/internal/**").permitAll()
 *   不生效导致 /internal/* 和部分 /entities/* 端点返回 403 的问题。
 *   根因：Spring Security 6.x PathPatternParser 对多层嵌套路径的匹配存在边界问题，
 *   且 GatewayAuthFilter 作为 @Component 被 servlet 容器自动注册，与 addFilterBefore
 *   产生 OncePerRequestFilter 重复执行抑制，导致部分路径未设置认证。
 *   解决：统一 permitAll（网关已鉴权），保留 GatewayAuthFilter 用于用户上下文。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewayAuthFilter gatewayAuthFilter;

    public SecurityConfig(GatewayAuthFilter gatewayAuthFilter) {
        this.gatewayAuthFilter = gatewayAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
