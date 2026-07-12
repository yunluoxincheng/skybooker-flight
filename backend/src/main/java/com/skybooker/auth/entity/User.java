package com.skybooker.auth.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {

    private Long id;
    private String email;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String role;
    private String status;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
