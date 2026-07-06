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
import com.skybooker.ai.state.ConversationState;
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

    /** 问候/能力介绍信号 → {@link DomainIntent#TRAVEL_CHAT}。 */
    private static final List<String> GREETING_KEYWORDS = List.of(
            "你好", "您好", "嗨", "hi", "hello", "在吗", "你是谁", "能做什么", "可以做什么", "介绍一下"
    );
    /** 旅游/目的地内容信号 → {@link DomainIntent#TRAVEL_CHAT}。 */
    private static final List<String> TRAVEL_ADVICE_KEYWORDS = List.of(
            "旅游", "旅行", "好玩", "推荐去玩", "旅行推荐", "旅游推荐", "攻略", "景点", "怎么玩",
            "玩几天", "行程", "美食", "住宿", "预算", "适合去", "目的地推荐", "在哪里", "多远",
            "距离", "怎么安排", "花费", "费用", "大概要花"
    );
    /** 平台动作 / 流程型信号 → {@link DomainIntent#BOOKING_HELP}。 */
    private static final List<String> BOOKING_HELP_KEYWORDS = List.of(
            "退票", "退款", "改签", "改期", "候补", "乘机人", "乘客", "订单", "支付", "账号",
            "账户", "登录", "发票", "怎么订", "如何订", "预订流程", "怎么操作", "如何操作",
            "流程", "规则", "说明", "平台", "选座", "skybooker", "SkyBooker"
    );
    private static final List<String> BOOKING_ACTION_KEYWORDS = List.of(
            "退票", "退款", "改签", "改期", "候补", "订单", "支付", "账号", "账户", "登录",
            "发票", "选座", "平台", "skybooker", "SkyBooker"
    );
    private static final List<String> HELP_CUE_KEYWORDS = List.of(
            "怎么", "如何", "流程", "操作", "规则", "说明", "添加", "维护", "查看", "取消", "发起"
    );
    /** 越界信号 → {@link DomainIntent#OUT_OF_SCOPE}。 */
    private static final List<String> OUT_OF_SCOPE_KEYWORDS = List.of(
            "写代码", "股票", "基金", "彩票", "作业", "论文", "游戏攻略", "电影", "天气预报",
            "医疗", "法律", "贷款", "菜谱"
    );

    // —— 第二层·航班事实信号(决定是否进入 FLIGHT_QUERY 执行器)——
    // 注意：只有“明确航班事实词”才直接触发 FLIGHT_QUERY；裸路线/裸目的地不再当航班信号，
    // 它们走 TRAVEL_CHAT 由回复器给澄清文案，避免“去北京有什么好玩”被误判成查机票。

    private static final List<String> FLIGHT_FACT_KEYWORDS = List.of(
            "机票", "航班", "飞机票", "票价", "时刻", "班次", "余票", "座位",
            "舱", "直飞", "直达", "航空", "航司", "查票", "有没有票", "起飞", "降落"
    );
    private static final List<String> PRICE_QUERY_KEYWORDS = List.of(
            "价格", "多少钱", "预算", "花费", "费用", "要花"
    );
    private static final List<String> TICKET_QUERY_INTENT_KEYWORDS = List.of(
            "查票", "买票", "订票", "预订", "查机票", "买机票", "订机票", "查航班", "订航班", "买飞机票"
    );

    private static final Pattern CITY_DESTINATION = Pattern.compile("(?:我想|想|我要|帮我)?(?:去|飞往|飞)[\\u4e00-\\u9fa5]{2,8}");
    private static final Pattern ROUTE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}(?:到|飞|去)[\\u4e00-\\u9fa5]{2,8}");
    private static final Pattern TRIP_DURATION_PATTERN = Pattern.compile(
            "(?:玩|旅游|旅行|行程)[一二两三四五六七八九十\\d]+天|[一二两三四五六七八九十\\d]+天.*(?:怎么玩|怎么安排|预算|花|费用|行程)");

    private final DynamicLlmConfigProvider configProvider;
    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper;
    private final IntentParserService ruleIntentParserService;

    public RouteResult route(String message, AiChatMessage previousAssistant) {
        LlmEffectiveConfig cfg = configProvider.getConfig();
        return routeInternal(message, cfg, hasPendingFollowUp(previousAssistant), null);
    }

    public RouteResult routeWithState(String message, ConversationState state) {
        LlmEffectiveConfig cfg = configProvider.getConfig();
        return routeInternal(message, cfg, state != null && state.hasPendingFlightQuery(), state);
    }

    private RouteResult routeInternal(String message, LlmEffectiveConfig cfg,
                                      boolean hasPendingFollowUp, ConversationState state) {
        String text = message == null ? "" : message.trim();

        // 多轮缺槽优先：上一条助手消息是 FOLLOW_UP 且带 missingFields 时，先看用户是否明确切换到
        // 非搜索意图；否则任何“在补全航班查询条件”的回复都应继续走 FLIGHT_QUERY_CONTINUATION。
        if (hasPendingFollowUp) {
            DomainIntent explicitNonSearch = explicitNonSearchRoute(text);
            if (explicitNonSearch != null) {
                return new RouteResult(explicitNonSearch, cfg, false);
            }
            if (isContinuationSlotFill(text)) {
                return new RouteResult(DomainIntent.FLIGHT_QUERY_CONTINUATION, cfg, false);
            }
        }

        if (isDestinationSwitchAfterRecommendation(text, state)) {
            ParsedCondition parsed = ruleIntentParserService.parse(text);
            if (hasFlightSearchSignal(text) || hasParsedSearchSignal(parsed)) {
                return new RouteResult(DomainIntent.FLIGHT_QUERY, cfg, false);
            }
            return new RouteResult(DomainIntent.TRAVEL_CHAT, cfg, false);
        }

        if (isFlightSearchAfterDestinationRecommendation(text, state)) {
            return new RouteResult(DomainIntent.FLIGHT_QUERY, cfg, false);
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
        // 步骤 2：平台帮助优先（退票/改签/订单/支付/订票流程等）。流程型问题即使提到“机票”
        // 也应走平台帮助，例如“怎么订票”、“如何订机票”、“预订流程是什么”。
        if (isBookingHelpQuestion(text)) {
            return DomainIntent.BOOKING_HELP;
        }
        // 步骤 3：明确航班事实词 → 查询（机票/票价/余票/舱位/航司/直飞/时刻等）。
        // “价格/多少钱/预算”是条件信号：只有绑定明确航班词、查票意图或路线+日期/舱位等查询信号时才查库，
        // 避免“去北京玩三天要多少钱”这类旅行预算问题被误判为机票查询。
        if (hasExplicitFlightFactSignal(text) || hasConditionalPriceFlightSignal(text)) {
            return DomainIntent.FLIGHT_QUERY;
        }
        // 步骤 4：旅游内容 → 闲聊（景点/怎么玩/攻略/在哪里/多远/玩几天/怎么安排等）。
        if (isTravelPlanningSignal(text)) {
            return DomainIntent.TRAVEL_CHAT;
        }
        // 步骤 5：问候 → 闲聊。
        if (isGreeting(lower)) {
            return DomainIntent.TRAVEL_CHAT;
        }
        // 步骤 6：裸路线/裸目的地（“上海到北京”、“我想去北京”）只有在带了额外查询信号
        // （日期/舱位/价格/航司/时段等）时才查库；否则按旅游闲聊给澄清文案。
        if (ROUTE_PATTERN.matcher(text).find() || CITY_DESTINATION.matcher(text).matches()) {
            if (hasExtraQuerySignal(ruleIntentParserService.parse(text))) {
                return DomainIntent.FLIGHT_QUERY;
            }
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
        // 缺槽上下文里，平台帮助只在不是明确航班事实查询时才接管。
        if (isBookingHelpQuestion(text)) {
            return DomainIntent.BOOKING_HELP;
        }
        if (isTravelPlanningSignal(text)) {
            return DomainIntent.TRAVEL_CHAT;
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
                || text.contains("景点")
                || text.contains("在哪里")
                || text.contains("多远")
                || text.contains("距离")
                || text.contains("玩几天")
                || text.contains("怎么安排")
                || (CITY_DESTINATION.matcher(text).find() && text.contains("玩"));
    }

    private boolean isTravelPlanningSignal(String text) {
        return isDestinationContent(text)
                || containsAny(text, TRAVEL_ADVICE_KEYWORDS)
                || TRIP_DURATION_PATTERN.matcher(text).find();
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT)) || text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBookingHelpQuestion(String text) {
        if (isStandaloneBookingHelp(text)) {
            return true;
        }
        boolean mentionsBooking = text.contains("订票") || text.contains("预订") || text.contains("订机票");
        boolean helpCue = containsAny(text, HELP_CUE_KEYWORDS);
        if (helpCue && (mentionsBooking || containsAny(text, BOOKING_HELP_KEYWORDS))) {
            return true;
        }
        return containsAny(text, BOOKING_ACTION_KEYWORDS) && !hasFlightSearchSignal(text);
    }

    private boolean hasExplicitFlightFactSignal(String text) {
        return containsAny(text, FLIGHT_FACT_KEYWORDS);
    }

    private boolean hasConditionalPriceFlightSignal(String text) {
        if (!containsAny(text, PRICE_QUERY_KEYWORDS)) {
            return false;
        }
        if (hasExplicitFlightFactSignal(text) || containsAny(text, TICKET_QUERY_INTENT_KEYWORDS)) {
            return true;
        }
        if (!(ROUTE_PATTERN.matcher(text).find() || CITY_DESTINATION.matcher(text).matches())) {
            return false;
        }
        return hasExtraQuerySignal(ruleIntentParserService.parse(text));
    }

    private boolean isStandaloneBookingHelp(String text) {
        String compact = text.trim();
        return compact.equals("订票") || compact.equals("预订") || compact.equals("订机票");
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
     * <p>不使用“短回复长度”启发式。仅认可以下语义信号：
     * <ul>
     *   <li>含航班事实关键词、城市路由或“去/飞+目的地”模式；</li>
     *   <li>规则 parser 已从文本中解析出任一查询字段（城市/日期/舱位/价格/时段/航司/直飞/排序/时长）；</li>
     *   <li>文本本身是裸补全词（已知城市名、乘客数量词），由
     *       {@link IntentParserService#looksLikeSlotFiller(String)} 判定；</li>
     *   <li>parser 针对歧义日期范围给出的“选一个出发日期”追问。</li>
     * </ul>
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
        return hasExplicitFlightFactSignal(text)
                || hasConditionalPriceFlightSignal(text)
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
                || parsed.getDepartureDateStart() != null
                || parsed.getDepartureDateEnd() != null
                || parsed.getCabinClass() != null
                || parsed.getAirlineRaw() != null
                || parsed.getMaxPrice() != null
                || parsed.getDepartureTimeStart() != null
                || parsed.getDepartureTimeEnd() != null
                || parsed.getMaxDurationMinutes() != null
                || parsed.getDirectOnly() != null
                || parsed.getSort() != null;
    }

    /**
     * 裸路线/裸目的地场景下的“额外查询信号”：日期/舱位/价格/航司/时段/时长/直飞/排序等。
     *
     * <p>不含城市——城市属于路线本身，不能单独作为“用户想查机票”的证据。
     * 这样“上海到北京”/“我想去北京”走澄清，“上海到北京明天”/“帮我查上海到北京经济舱”走查询。
     */
    private boolean hasExtraQuerySignal(ParsedCondition parsed) {
        if (parsed == null) {
            return false;
        }
        // 歧义日期范围（“最近几天”/“周一周二都可以”等）也视为查询信号：用户在表达出行日期，
        // 应进入 FLIGHT_QUERY 链路并由 parser 追问一个具体出发日期，而不是走 TRAVEL_CHAT 澄清。
        boolean ambiguousDateFollowUp = parsed.getFollowUpQuestion() != null
                && parsed.getFollowUpQuestion().contains("一个出发日期");
        return parsed.getDepartureDate() != null
                || (parsed.getDepartureDateStart() != null && parsed.getDepartureDateEnd() != null)
                || parsed.getCabinClass() != null
                || parsed.getAirlineRaw() != null
                || parsed.getMaxPrice() != null
                || parsed.getMinPrice() != null
                || parsed.getDepartureTimeStart() != null
                || parsed.getDepartureTimeEnd() != null
                || parsed.getMaxDurationMinutes() != null
                || parsed.getDirectOnly() != null
                || parsed.getSort() != null
                || ambiguousDateFollowUp;
    }

    private boolean hasSpecificFollowUp(ParsedCondition parsed) {
        return parsed != null
                && parsed.getFollowUpQuestion() != null
                && parsed.getFollowUpQuestion().contains("一个出发日期");
    }

    private boolean isFlightSearchAfterDestinationRecommendation(String text, ConversationState state) {
        if (state == null || state.getRecommendedDestinationCity() == null || text.isBlank()) {
            return false;
        }
        ParsedCondition parsed = ruleIntentParserService.parse(text);
        if (hasFlightSearchSignal(text)) {
            return true;
        }
        return parsed.getDepartureCity() != null
                || parsed.getArrivalCity() != null
                || parsed.getDepartureDate() != null
                || parsed.getDepartureDateStart() != null
                || parsed.getDepartureDateEnd() != null
                || parsed.getCabinClass() != null
                || parsed.getAirlineRaw() != null
                || parsed.getMaxPrice() != null
                || parsed.getDepartureTimeStart() != null
                || parsed.getDepartureTimeEnd() != null
                || parsed.getMaxDurationMinutes() != null
                || parsed.getDirectOnly() != null
                || parsed.getSort() != null;
    }

    private boolean isDestinationSwitchAfterRecommendation(String text, ConversationState state) {
        return state != null
                && state.getRecommendedDestinationCity() != null
                && ruleIntentParserService.parseDestinationSwitchCity(text) != null;
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
                价格/多少钱/预算只有在同时出现明确航班词、查票/买票/订票意图，或完整路线+日期/舱位等查询信号时，才返回 FLIGHT_QUERY。
                “怎么订票/退票/改签/订单/支付/流程/规则”等平台功能问题返回 BOOKING_HELP。
                目的地怎么玩、景点、行程、在哪里、多远、攻略、旅行预算、问候、助手能力介绍等旅游出行闲聊返回 TRAVEL_CHAT。
                “我想去北京”“上海到北京”这类只表达目的地/路线、但未明确问机票的，返回 TRAVEL_CHAT。
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
