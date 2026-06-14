package com.jones.kb.service;

import com.jones.kb.dto.auth.LoginRequest;
import com.jones.kb.dto.auth.LoginResponse;
import com.jones.kb.dto.auth.RefreshRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout(String accessToken);

    LoginResponse refresh(RefreshRequest request);
}
