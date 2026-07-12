package com.skybooker.admin.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConnectingItineraryQueryDTO extends PageQueryDTO {
    private String keyword;
    private String segmentScope = "ALL";
}
