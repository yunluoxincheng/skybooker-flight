package com.skybooker.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.enums.AiReplyType;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.parser.IntentParserService;
import com.skybooker.ai.parser.LlmChatClient;
import com.skybooker.ai.parser.ParsedCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainIntentRouter {

    // —— 第一层·领域路由关键词 ——

    /** 问候/能力介绍信号 → {@link DomainIntent#TRAVEL_CHAT}（与旅游闲聊同走 LLM 限话题回复）。 */
    private static final List<String> GREETING_KEYWORDS = List.of(
            "你好", "您好", "嗨", "hi", "hello", "在吗", "你是谁", "能做什么", "可以做什么", "介绍一下"
    );
    /** 旅游/目的地内容信号 → {@link DomainIntent#TRAVEL_CHAT}。 */
    private static final List<String> TRAVEL_ADVICE_KEYWORDS = List.of(
            "旅游", "旅行", "好玩", "推荐去玩", "旅行推荐", "旅游推荐", "攻略", "景点", "怎么玩",
            "玩几天", "行程", "美食", "住宿", "预算", "适合去", "目的地推荐"
    );
    /** 平台功能信号 → {@link DomainIntent#BOOKING_HELP}。 */
    private static final List<String> BOOKING_HELP_KEYWORDS = List.of(
            "怎么订", "如何订", "预订流程", "退票", "退款", "改签", "改期", "候补", "乘机人",
            "乘客", "订单", "支付", "账号", "账户", "登录", "发票", "平台", "skybooker", "SkyBooker"
    );
    /** 越界信号 → {@link DomainIntent#OUT_OF_SCOPE}。 */
    private static final List<String> OUT_OF_SCOPE_KEYWORDS = List.of(
            "写代码", "股票", "基金", "彩票", "作业", "论文", "游戏攻略", "电影", "天气预报",
            "医疗", "法律", "贷款", "菜谱"
    );

    // —— 第二层·航班事实信号（决定是否进入 FLIGHT_QUERY 执行器）——

    private static final List<String> FLIGHT_KEYWORDS = List.of(
            "机票", "航班", "飞机票", "票价", "价格", "多少钱", "时刻", "班次", "余票", "座位",
            "舱", "直飞", "直达", "航空", "航司", "订票", "预订", "可订", "有没有票", "查票"
    );

    private static final Pattern CITY_DESTINATION = Pattern.compile("(?:我想|想|我要|帮我)?(?:去|飞往|飞)[\\u4e00-\\u9fa5]{2,8}");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}(?:到|飞|去)[\\u4e00-\\u9fa5]{2,8}");

    private final DynamicLlmConfigProvider configProvider;
    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper;
    private final IntentParserService ruleIntentParserService;

    public RouteResult route(String message, AiChatMessage previousAssistant) {
        LlmEffectiveConfig cfg = configProvider.getConfig();
        String text = message == null ? "" : message.trim();

        // 多轮缺槽优先：上一条助手消息是 FOLLOW_UP 且带 missingFields 时，先看用户是否明确切换到
        // 非搜索意图；否则任何"在补全航班查询条件"的回复都应继续走 FLIGHT_QUERY_CONTINUATION。
        if (hasPendingFollowUp(previousAssistant)) {
            DomainIntent explicitNonSearch = explicitNonSearchRoute(text);
            if (explicitNonSearch != null) {
                return new RouteResult(explicitNonSearch, cfg, false);
            }
            if (isContinuationSlotFill(text)) {
                return new RouteResult(DomainIntent.FLIGHT_QUERY_CONTINUATION, cfg, false);
            }
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
            return DomainIntent.FLIGHT_QUERY;
        }

        String lower = text.toLowerCase(Locale.ROOT);

        // 步骤 1：是否在旅游/出行领域之外？
        if (containsAny(lower, OUT_OF_SCOPE_KEYWORDS)) {
            return DomainIntent.OUT_OF_SCOPE;
        }
        // 步骤 2：领域之内 —— 航班事实优先（机票/票价/余票/舱位/路由等必须走数据库查询）。
        if (hasFlightSearchSignal(text)) {
            return DomainIntent.FLIGHT_QUERY;
        }
        if (isDestinationContent(text) || containsAny(text, TRAVEL_ADVICE_KEYWORDS)) {
            return DomainIntent.TRAVEL_CHAT;
        }
        if (containsAny(text, BOOKING_HELP_KEYWORDS) && !ROUTE_PATTERN.matcher(text).find()) {
            return DomainIntent.BOOKING_HELP;
        }
        if (isGreeting(lower)) {
            return DomainIntent.TRAVEL_CHAT;
        }
        return null;
    }

    private DomainIntent explicitNonSearchRoute(String text) {
        if (text.isBlank()) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, OUT_OF_SCOPE_KEYWORDS)) {
            return DomainIntent.OUT_OF_SCOPE;
        }
        if (isDestinationContent(text) || containsAny(text, TRAVEL_ADVICE_KEYWORDS)) {
            return DomainIntent.TRAVEL_CHAT;
        }
        if (containsAny(text, BOOKING_HELP_KEYWORDS) && !hasFlightSearchSignal(text)) {
            return DomainIntent.BOOKING_HELP;
        }
        if (isGreeting(lower)) {
            return DomainIntent.TRAVEL_CHAT;
        }
        return null;
    }

    private boolean isGreeting(String lower) {
        for (String keyword : GREETING_KEYWORDS) {
            String k = keyword.toLowerCase(Locale.ROOT);
            if (lower.equals(k) || lower.contains(k)) {
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

    /**
     * 判断当前回复是否在补全上一轮 FOLLOW_UP 缺失的航班查询条件。
     *
     * <p>不使用"短回复长度"启发式。仅认可以下语义信号：
     * <ul>
     *   <li>含航班查询关键词、城市路由或"去/飞+目的地"模式；</li>
     *   <li>规则 parser 已从文本中解析出任一查询字段（城市/日期/舱位/价格/时段/航司/直飞/排序/时长）；</li>
     *   <li>文本本身是裸补全词（已知城市名、乘客数量词），由
     *       {@link IntentParserService#looksLikeSlotFiller(String)} 判定；</li>
     *   <li>parser 针对歧义日期范围给出的"选一个出发日期"追问。</li>
     * </ul>
     * 这样既覆盖"明天"/"北京"/"两个人"等短补全，也覆盖"我想明天上午从广州出发"等自然补全，
     * 同时不会把"写代码"等越界回复误判为 continuation。
     */
    private boolean isContinuationSlotFill(String text) {
        if (text.isBlank()) {
            return false;
        }
        if (hasFlightSearchSignal(text)) {
            return true;
        }

        ParsedCondition parsed = ruleIntentParserService.parse(text);
        return hasParsedSearchSignal(parsed)
                || ruleIntentParserService.looksLikeSlotFiller(text)
                || hasSpecificFollowUp(parsed);
    }

    private boolean hasFlightSearchSignal(String text) {
        return containsAny(text, FLIGHT_KEYWORDS)
                || ROUTE_PATTERN.matcher(text).find()
                || CITY_DESTINATION.matcher(text).matches();
    }

    private boolean hasParsedSearchSignal(ParsedCondition parsed) {
        if (parsed == null) {
            return false;
        }
        return parsed.getDepartureCity() != null
                || parsed.getArrivalCity() != null
                || parsed.getDepartureDate() != null
                || parsed.getCabinClass() != null
                || parsed.getAirlineRaw() != null
                || parsed.getMaxPrice() != null
                || parsed.getDepartureTimeStart() != null
                || parsed.getDepartureTimeEnd() != null
                || parsed.getMaxDurationMinutes() != null
                || parsed.getDirectOnly() != null
                || parsed.getSort() != null;
    }

    private boolean hasSpecificFollowUp(ParsedCondition parsed) {
        return parsed != null
                && parsed.getFollowUpQuestion() != null
                && parsed.getFollowUpQuestion().contains("一个出发日期");
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
                intent 必须是 TRAVEL_CHAT、FLIGHT_QUERY、BOOKING_HELP、OUT_OF_SCOPE 之一。
                只要用户询问机票、航班、票价、时刻、余票、舱位、航司、直飞或可预订选项，返回 FLIGHT_QUERY。
                目的地怎么玩、景点、行程、预算、问候、助手能力介绍等旅游出行闲聊返回 TRAVEL_CHAT。
                SkyBooker 订票、退票、改签、候补、乘机人、订单或账号帮助返回 BOOKING_HELP。
                非旅行、非航班、非 SkyBooker 平台帮助返回 OUT_OF_SCOPE。
                示例：{"intent":"TRAVEL_CHAT"}
                """;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replaceAll("(?i)(Authorization\\s*:\\s*)(Bearer\\s+)?[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll("(?i)((?:x-)?api-key\\s*[:=]?\\s*)[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9._-]+", "sk-***");
    }

    public record RouteResult(DomainIntent intent, LlmEffectiveConfig llmConfig, boolean llmClassified) {
    }
}
