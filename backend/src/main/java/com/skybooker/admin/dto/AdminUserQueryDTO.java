package com.skybooker.admin.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends PageQueryDTO {
    private String keyword;
    private String email;
    private String nickname;
    private String status;
}
