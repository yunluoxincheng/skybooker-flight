package com.skybooker.ai.controller;

import com.skybooker.ai.dto.AiChatRequest;
import com.skybooker.ai.service.AiChatService;
import com.skybooker.ai.vo.AiChatReplyVO;
import com.skybooker.ai.vo.AiSessionMessagesVO;
import com.skybooker.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ApiResponse<AiChatReplyVO> chat(@Valid @RequestBody AiChatRequest request) {
        AiChatReplyVO reply = aiChatService.chat(request.getSessionId(), request.getMessage());
        return ApiResponse.success(reply);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<AiSessionMessagesVO> getSessionMessages(@PathVariable String sessionId) {
        return ApiResponse.success(aiChatService.getSessionMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        aiChatService.deleteSession(sessionId);
        return ApiResponse.success();
    }
}
