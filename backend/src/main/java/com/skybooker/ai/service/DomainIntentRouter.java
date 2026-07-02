package com.skybooker.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.parser.LlmChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainIntentRouter {

    private static final int SHORT_SLOT_FILL_MAX_LENGTH = 12;

    private static final List<String> GREETING_KEYWORDS = List.of(
            "你好", "您好", "嗨", "hi", "hello", "在吗", "你是谁", "能做什么", "可以做什么", "介绍一下"
    );
    private static final List<String> FLIGHT_KEYWORDS = List.of(
            "机票", "航班", "飞机票", "票价", "价格", "多少钱", "时刻", "班次", "余票", "座位",
            "舱", "直飞", "直达", "航空", "航司", "订票", "预订", "可订", "有没有票", "查票"
    );
    private static final List<String> TRAVEL_ADVICE_KEYWORDS = List.of(
            "旅游", "旅行", "好玩", "推荐去玩", "旅行推荐", "旅游推荐", "攻略", "景点", "怎么玩",
            "玩几天", "行程", "美食", "住宿", "预算", "适合去", "目的地推荐"
    );
    private static final List<String> PLATFORM_HELP_KEYWORDS = List.of(
            "怎么订", "如何订", "预订流程", "退票", "退款", "改签", "改期", "候补", "乘机人",
            "乘客", "订单", "支付", "账号", "账户", "登录", "发票", "平台", "skybooker", "SkyBooker"
    );
    private static final List<String> OUT_OF_SCOPE_KEYWORDS = List.of(
            "写代码", "股票", "基金", "彩票", "作业", "论文", "游戏攻略", "电影", "天气预报",
            "医疗", "法律", "贷款", "菜谱"
    );

    private static final Pattern CITY_DESTINATION = Pattern.compile("(?:我想|想|我要|帮我)?(?:去|飞往|飞)[\\u4e00-\\u9fa5]{2,8}");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}(?:到|飞|去)[\\u4e00-\\u9fa5]{2,8}");

    private final DynamicLlmConfigProvider configProvider;
    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper;

    public RouteResult route(String message, AiChatMessage previousAssistant) {
        LlmEffectiveConfig cfg = configProvider.getConfig();
        String text = message == null ? "" : message.trim();

        if (hasPendingFollowUp(previousAssistant) && isShortSlotFill(text)) {
            return new RouteResult(DomainIntent.FLIGHT_SEARCH_CONTINUATION, cfg, false);
        }

        DomainIntent deterministic = deterministicRoute(text);
        if (deterministic != null) {
            return new RouteResult(deterministic, cfg, false);
        }

        if (cfg.isConfigured()) {
            DomainIntent llmIntent = classifyWithLlm(text, cfg);
            if (llmIntent != null) {
                return new RouteResult(llmIntent, cfg, true);
            }
        }

        return new RouteResult(DomainIntent.OUT_OF_SCOPE, cfg, false);
    }

    private DomainIntent deterministicRoute(String text) {
        if (text.isBlank()) {
            return DomainIntent.FLIGHT_SEARCH;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, OUT_OF_SCOPE_KEYWORDS)) {
            return DomainIntent.OUT_OF_SCOPE;
        }
        if (isDestinationContent(text)) {
            return DomainIntent.TRAVEL_ADVICE;
        }
        if (containsAny(text, PLATFORM_HELP_KEYWORDS) && !ROUTE_PATTERN.matcher(text).find()) {
            return DomainIntent.PLATFORM_HELP;
        }
        if (isGreeting(lower)) {
            return DomainIntent.GREETING;
        }
        if (containsAny(text, FLIGHT_KEYWORDS) || ROUTE_PATTERN.matcher(text).find()) {
            return DomainIntent.FLIGHT_SEARCH;
        }
        if (CITY_DESTINATION.matcher(text).matches()) {
            return DomainIntent.FLIGHT_SEARCH;
        }
        if (containsAny(text, TRAVEL_ADVICE_KEYWORDS)) {
            return DomainIntent.TRAVEL_ADVICE;
        }
        return null;
    }

    private boolean isGreeting(String lower) {
        for (String keyword : GREETING_KEYWORDS) {
            if (lower.equals(keyword.toLowerCase(Locale.ROOT)) || lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isDestinationContent(String text) {
        return text.contains("有什么好玩")
                || text.contains("旅行推荐")
                || text.contains("旅游推荐")
                || text.contains("适合玩")
                || text.contains("怎么玩")
                || text.contains("攻略")
                || text.contains("景点");
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT)) || text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private DomainIntent classifyWithLlm(String text, LlmEffectiveConfig cfg) {
        try {
            String content = llmChatClient.complete(classificationPrompt(), text, cfg);
            JsonNode root = objectMapper.readTree(stripCodeFence(content));
            String value = root.path("intent").asText("").trim();
            return DomainIntent.valueOf(value);
        } catch (Exception e) {
            log.warn("LLM domain intent routing failed, falling back to deterministic routing: {}", sanitizeErrorMessage(e.getMessage()));
            return null;
        }
    }

    private boolean hasPendingFollowUp(AiChatMessage previousAssistant) {
        if (previousAssistant == null || previousAssistant.getExtraJson() == null) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(previousAssistant.getExtraJson());
            boolean followUp = AiReplyType.FOLLOW_UP.name().equals(root.path("replyType").asText());
            JsonNode missingFields = root.path("missingFields");
            return followUp && missingFields.isArray() && !missingFields.isEmpty();
        } catch (Exception e) {
            log.warn("Failed to inspect previous assistant follow-up metadata: {}", sanitizeErrorMessage(e.getMessage()));
            return false;
        }
    }

    private boolean isShortSlotFill(String text) {
        if (text.isBlank() || text.length() > SHORT_SLOT_FILL_MAX_LENGTH) {
            return false;
        }
        return !containsAny(text, FLIGHT_KEYWORDS)
                && !containsAny(text, TRAVEL_ADVICE_KEYWORDS)
                && !containsAny(text, PLATFORM_HELP_KEYWORDS);
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String classificationPrompt() {
        return """
                你是 SkyBooker 后端意图分类器。只返回 JSON，不要 Markdown。
                intent 必须是 GREETING、TRAVEL_ADVICE、PLATFORM_HELP、FLIGHT_SEARCH、OUT_OF_SCOPE 之一。
                只要用户询问机票、航班、票价、时刻、余票、舱位、航司、直飞或可预订选项，返回 FLIGHT_SEARCH。
                目的地怎么玩、景点、行程、预算等非具体航班事实返回 TRAVEL_ADVICE。
                SkyBooker 订票、退票、改签、候补、乘机人、订单或账号帮助返回 PLATFORM_HELP。
                非旅行、非航班、非 SkyBooker 平台帮助返回 OUT_OF_SCOPE。
                示例：{"intent":"TRAVEL_ADVICE"}
                """;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9._-]+", "sk-***");
    }

    public record RouteResult(DomainIntent intent, LlmEffectiveConfig llmConfig, boolean llmClassified) {
    }
}
