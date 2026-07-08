package com.skybooker.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderTimelineItemVO {
    private String eventType;
    private String status;
    private String description;
    private LocalDateTime occurredAt;
}
