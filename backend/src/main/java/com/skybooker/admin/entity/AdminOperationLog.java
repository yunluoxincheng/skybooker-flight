package com.skybooker.admin.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminOperationLog {

    private Long id;
    private Long adminUserId;
    private String targetType;
    private Long targetId;
    private String action;
    private String reason;
    private LocalDateTime createdAt;
}
