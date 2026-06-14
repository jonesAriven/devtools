package com.jones.kb.service;

import com.jones.kb.entity.User;

public interface UserService {

    User getProfile(Long userId);

    User updateProfile(Long userId, String nickname, String email, String phone, String avatar);

    void updatePassword(Long userId, String oldPassword, String newPassword);
}
