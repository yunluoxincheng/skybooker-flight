package com.skybooker.passenger.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Passenger {
    private Long id;
    private Long userId;
    private String name;
    private String idCardNo;
    private String passengerType;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
