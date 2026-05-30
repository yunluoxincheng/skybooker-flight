package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminVO {

    private Long id;
    private Long userId;
    private String username;
    private String realName;
    private String role;
}
