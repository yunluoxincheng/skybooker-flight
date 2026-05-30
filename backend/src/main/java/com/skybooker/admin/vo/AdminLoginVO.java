package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminLoginVO {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private AdminVO admin;
}
