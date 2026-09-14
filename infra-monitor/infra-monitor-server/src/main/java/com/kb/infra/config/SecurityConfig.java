package com.kb.infra.config;

import com.kb.infra.filter.JwtAuthFilter;
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

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
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
                .requestMatchers("/auth/login", "/actuator/**").permitAll()
                // 统一登录三方式（Phase 7）：邮箱验证码发码/登录为匿名端点
                // （登录前无 token，必须在过滤器链放行；真正校验由 auth-center 承担）
                .requestMatchers("/auth/mail-login", "/auth/mail-login/send-code").permitAll()
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
