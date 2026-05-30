package com.skybooker.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UserVO user;
}
