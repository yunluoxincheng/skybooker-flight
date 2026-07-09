package com.skybooker.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAccountCancellationLog {

    private Long id;
    private Long userId;
    private String action;
    private String clientIp;
    private LocalDateTime createdAt;
}
