package com.jones.activation.controller;

import com.jones.activation.entity.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
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

    @GetMapping("/activecode/login.html")
    public ResponseEntity<String> loginPage() throws IOException {
        return readStaticFile("activecode/login.html");
    }

    @GetMapping("/activecode/index.html")
    public ResponseEntity<String> indexPage() throws IOException {
        return readStaticFile("activecode/index.html");
    }

    private ResponseEntity<String> readStaticFile(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/" + path);
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(content);
    }
}