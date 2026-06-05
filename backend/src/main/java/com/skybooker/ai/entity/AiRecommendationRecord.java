package com.skybooker.ai.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiRecommendationRecord {
    private Long id;
    private Long sessionId;
    private Long userId;
    private String queryText;
    private String parsedConditionJson;
    private String recommendedFlightIds;
    private String searchUrl;
    private LocalDateTime createdAt;
}
