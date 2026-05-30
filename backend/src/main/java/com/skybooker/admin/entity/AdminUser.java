package com.skybooker.admin.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminUser {

    private Long id;
    private Long userId;
    private String username;
    private String jobNo;
    private String realName;
    private String remark;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
