package com.jones.kb.controller;

import com.jones.kb.common.R;
import com.jones.kb.entity.User;
import com.jones.kb.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public R<User> getProfile() {
        return R.ok(userService.getProfile(getCurrentUserId()));
    }

    @PutMapping("/profile")
    public R<User> updateProfile(@RequestBody UpdateProfileRequest request) {
        return R.ok(userService.updateProfile(getCurrentUserId(),
                request.getNickname(), request.getEmail(), request.getPhone(), request.getAvatar()));
    }

    @PutMapping("/password")
    public R<Void> updatePassword(@RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(getCurrentUserId(), request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }

    @Data
    public static class UpdateProfileRequest {
        private String nickname;
        private String email;
        private String phone;
        private String avatar;
    }

    @Data
    public static class UpdatePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
