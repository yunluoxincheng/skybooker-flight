package com.skybooker.waitlist.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WaitlistPassenger {
    private Long id;
    private Long waitlistId;
    private Long passengerId;
    private String passengerName;
    private String passengerType;
    private Long lockedSeatId;
    private String lockedSeatNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
