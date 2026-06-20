package com.kb.knowledge.feign.dto;

import lombok.Data;

/**
 * 用户信息 DTO（通过 Feign 从 kb-auth 获取）
 */
@Data
public class UserDTO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String avatar;

    private Integer status;
}
