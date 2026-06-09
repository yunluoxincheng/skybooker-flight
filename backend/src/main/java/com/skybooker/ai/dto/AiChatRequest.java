package com.skybooker.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    private String sessionId;

    @NotBlank(message = "消息不能为空")
    private String message;
}
