package com.skybooker.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionMessageVO {
    private String role;
    private String content;
    private String messageType;
    private Map<String, Object> extra;
    private LocalDateTime createdAt;
}
