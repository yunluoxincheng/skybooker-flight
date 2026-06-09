package com.skybooker.ai.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiChatSession {
    private Long id;
    private String publicSessionId;
    private Long userId;
    private String sessionTitle;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
