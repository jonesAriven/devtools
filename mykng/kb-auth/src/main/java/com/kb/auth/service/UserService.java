package com.kb.auth.service;

import com.kb.auth.entity.User;

import java.util.List;

public interface UserService {

    User getProfile(Long userId);

    User updateProfile(Long userId, String nickname, String email, String phone, String avatar);

    void updatePassword(Long userId, String oldPassword, String newPassword);

    List<User> listAll();
}
