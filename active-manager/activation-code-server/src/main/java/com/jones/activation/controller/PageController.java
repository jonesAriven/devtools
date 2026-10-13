package com.jones.activation.controller;

import com.jones.activation.entity.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class PageController {

    @GetMapping("/activecode/main.html")
    public ResponseEntity<String> mainPage(HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/activecode/login.html")
                    .build();
        }
        return readStaticFile("activecode/main.html");
    }

    /** 本系统用户管理页：与 main.html 同口径做登录态守卫（未登录直接回登录页）。 */
    @GetMapping("/activecode/members.html")
    public ResponseEntity<String> membersPage(HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/activecode/login.html")
                    .build();
        }
        return readStaticFile("activecode/members.html");
    }

    @GetMapping("/activecode/login.html")
    public ResponseEntity<String> loginPage() throws IOException {
        return readStaticFile("activecode/login.html");
    }

    @GetMapping("/activecode/sso-callback.html")
    public ResponseEntity<String> ssoCallbackPage() throws IOException {
        return readStaticFile("activecode/sso-callback.html");
    }

    @GetMapping("/activecode/index.html")
    public ResponseEntity<String> indexPage() throws IOException {
        return readStaticFile("activecode/index.html");
    }

    @GetMapping("/activecode/downloads.html")
    public ResponseEntity<String> downloadsPage() throws IOException {
        return readStaticFile("activecode/downloads.html");
    }

    private ResponseEntity<String> readStaticFile(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/" + path);
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // 🔴 2026-10-13：本 controller 直出全部 HTML 页面，抢在
        //    WebMvcConfig#addResourceHandlers 的 resource handler 之前命中
        //    （RequestMappingHandlerMapping order=0 < SimpleUrlHandlerMapping），
        //    因此 resource handler 上配的 setCacheControl(noCache) 对 HTML 路径
        //    **不生效**——此前所有页面响应都是「无 Cache-Control / 无 Last-Modified /
        //    无 ETag」的裸响应。
        //    浏览器对无缓存验证头的 HTML 走启发式缓存，会把旧版页面长期留在本地：
        //    表现为「服务端与线上 HTML 都已更新，用户看到的仍是旧页面」——
        //    10-12 index.html 兜底上线后「有效期卡在加载中」复发的直接根因。
        //    no-store：浏览器不得缓存副本，每次访问都必须回源取新版。
        //    Pragma: no-cache 兼容 HTTP/1.0 中间层。
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(content);
    }
}