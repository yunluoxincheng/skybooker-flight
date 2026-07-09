package com.skybooker.admin.dto;

import lombok.Data;

@Data
public class PageQueryDTO {
    private int page = 1;
    private int size = 10;
}
