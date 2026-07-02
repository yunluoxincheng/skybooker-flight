package com.skybooker.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatReplyVO {
    private String sessionId;
    private String replyType;
    private String intent;
    private String replyText;
    private Map<String, Object> parsedCondition;
    private List<String> missingFields;
    private String followUpQuestion;
    private String searchUrl;
    private List<Map<String, Object>> flights;
    private List<Map<String, String>> quickActions;
}
