package com.skybooker.ai.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String messageType;
    private String extraJson;
    private LocalDateTime createdAt;
}
