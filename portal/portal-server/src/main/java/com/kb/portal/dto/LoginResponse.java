package com.kb.portal.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String accessToken;

    private String username;

    private String nickname;

    private String role;

    /**
     * auth-center 的用户 id（OIDC access_token 的 uid/sub claim）。
     * 前端存下后供会话监视器的「身份一致性守卫」比对：
     * IdP 会话身份 ≠ 本地身份（共享浏览器换人）时静默重换票。
     */
    private String authUid;

    public LoginResponse(String accessToken, String username, String nickname) {
        this(accessToken, username, nickname, null, null);
    }

    public LoginResponse(String accessToken, String username, String nickname, String role) {
        this(accessToken, username, nickname, role, null);
    }

    public LoginResponse(String accessToken, String username, String nickname, String role, String authUid) {
        this.accessToken = accessToken;
        this.username = username;
        this.nickname = nickname;
        this.role = role == null ? "user" : role;
        this.authUid = authUid;
    }
}
