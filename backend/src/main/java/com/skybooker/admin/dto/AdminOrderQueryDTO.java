package com.skybooker.admin.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderQueryDTO extends PageQueryDTO {
    private String status;
    private String orderNo;
    private Long userId;
    private String userKeyword;
    private String flightNo;
    private String flightKeyword;
    private String departureDateStart;
    private String departureDateEnd;
}
