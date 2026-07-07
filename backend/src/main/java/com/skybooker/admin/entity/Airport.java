package com.skybooker.admin.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Airport {
    private Long id;
    private String code;
    private String name;
    private String city;
    private String province;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
