package com.kb.portal.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String accessToken;

    private String username;

    private String nickname;

    private String role;

    public LoginResponse(String accessToken, String username, String nickname) {
        this(accessToken, username, nickname, null);
    }

    public LoginResponse(String accessToken, String username, String nickname, String role) {
        this.accessToken = accessToken;
        this.username = username;
        this.nickname = nickname;
        this.role = role == null ? "user" : role;
    }
}
