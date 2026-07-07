package com.skybooker.admin.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Airline {
    private Long id;
    private String code;
    private String name;
    private String logoUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
