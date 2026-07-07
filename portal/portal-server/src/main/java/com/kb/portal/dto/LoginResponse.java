package com.kb.portal.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String accessToken;

    private String username;

    private String nickname;

    public LoginResponse(String accessToken, String username, String nickname) {
        this.accessToken = accessToken;
        this.username = username;
        this.nickname = nickname;
    }
}
