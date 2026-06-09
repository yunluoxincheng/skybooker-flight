package com.skybooker.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionMessagesVO {
    private String sessionId;
    private String status;
    private List<AiSessionMessageVO> messages;
}
