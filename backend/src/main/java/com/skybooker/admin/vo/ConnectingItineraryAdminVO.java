package com.skybooker.admin.vo;

import com.skybooker.flight.vo.FlightVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ConnectingItineraryAdminVO {
    private Long id;
    private FlightVO firstSegment;
    private FlightVO secondSegment;
    private String publishStatus;
    private Integer transferMinutes;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
