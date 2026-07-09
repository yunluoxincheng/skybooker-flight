package com.skybooker.admin.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminKeywordStatusQueryDTO extends PageQueryDTO {
    private String keyword;
    private String status;
}
