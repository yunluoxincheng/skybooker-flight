package com.skybooker.admin.dto;

import lombok.Data;

@Data
public class AdminOrderQueryDTO {
    private String status;
    private String orderNo;
    private Long userId;
    private String userKeyword;
    private String flightNo;
    private String flightKeyword;
    private String departureDateStart;
    private String departureDateEnd;
    private int page = 1;
    private int size = 10;
}
