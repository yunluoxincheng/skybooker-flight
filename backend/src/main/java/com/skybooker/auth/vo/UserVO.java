package com.skybooker.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String email;
    private String nickname;
    private String role;
}
