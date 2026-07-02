package com.skybooker.ai.service;

import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.parser.LlmChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainReplyComposer {

    private static final Pattern FLIGHT_NO_PATTERN = Pattern.compile("\\b[A-Z]{2}\\s?\\d{3,4}\\b");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:[￥¥]\\s?\\d+|\\d+\\s?(?:元|块))");
    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?://|/flights/|/booking/|detailUrl|bookingUrl)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEAT_OR_AVAILABILITY_PATTERN = Pattern.compile(
            "(?:余票|剩余\\s?\\d+|\\d+\\s?个?座位|可预订|可以预订|有票|库存|当前有|现在有)");

    private final LlmChatClient llmChatClient;

    public String compose(DomainIntent intent, String message, LlmEffectiveConfig cfg) {
        if (intent == DomainIntent.PLATFORM_HELP || intent == DomainIntent.OUT_OF_SCOPE) {
            return deterministicReply(intent, message);
        }

        if (cfg != null && cfg.isConfigured()) {
            try {
                String text = llmChatClient.complete(compositionPrompt(intent), message, cfg).trim();
                if (!text.isBlank() && !containsConcreteFlightFacts(text)) {
                    return text;
                }
                log.warn("LLM non-search reply failed safety validation for intent {}", intent);
            } catch (RuntimeException e) {
                log.warn("LLM non-search reply composition failed for intent {}: {}", intent, sanitizeErrorMessage(e.getMessage()));
            }
        }

        return deterministicReply(intent, message);
    }

    public boolean containsConcreteFlightFacts(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return FLIGHT_NO_PATTERN.matcher(text).find()
                || PRICE_PATTERN.matcher(text).find()
                || URL_PATTERN.matcher(text).find()
                || SEAT_OR_AVAILABILITY_PATTERN.matcher(text).find();
    }

    private String deterministicReply(DomainIntent intent, String message) {
        return switch (intent) {
            case GREETING -> "您好，我是 SkyBooker 航班助手。可以帮您查询机票、补全出发地/目的地/日期，也可以提供旅行相关建议。";
            case TRAVEL_ADVICE -> "可以的。我可以给您做旅行方向建议、目的地玩法思路和出行准备提醒；如果要查具体航班、价格或余票，请告诉我出发城市、目的地和出发日期。";
            case PLATFORM_HELP -> platformHelpReply(message);
            case OUT_OF_SCOPE -> "抱歉，这个问题超出了 SkyBooker 航班与旅行助手的范围。我可以继续帮您查询机票、规划旅行方向，或说明订票、退票、改签、候补、订单等平台流程。";
            case FLIGHT_SEARCH, FLIGHT_SEARCH_CONTINUATION -> "请告诉我出发城市、目的地和出发日期，我会基于 SkyBooker 的航班数据为您查询。";
        };
    }

    private String platformHelpReply(String message) {
        String text = message == null ? "" : message;
        if (text.contains("退票") || text.contains("退款")) {
            return "退票或退款请在 SkyBooker 订单详情中发起。系统会按订单状态和平台规则处理，具体可退金额以订单页面展示为准。";
        }
        if (text.contains("改签") || text.contains("改期")) {
            return "改签请从订单详情进入改签流程，选择可改签航班并按页面提示确认差价或退款信息。";
        }
        if (text.contains("候补")) {
            return "候补用于目标航班暂不可订时提交排队请求。候补结果、支付和取消请以 SkyBooker 候补订单页面展示为准。";
        }
        if (text.contains("乘机人") || text.contains("乘客")) {
            return "乘机人信息请在个人中心或下单流程中维护。提交订单前请核对姓名、证件号和联系方式。";
        }
        if (text.contains("订单") || text.contains("支付")) {
            return "订单和支付状态请在 SkyBooker 订单列表或订单详情中查看。未支付订单需在页面提示的有效期内完成支付。";
        }
        if (text.contains("账号") || text.contains("账户") || text.contains("登录")) {
            return "账号相关操作请使用 SkyBooker 登录、注册和个人中心页面完成；请勿在聊天中发送密码、验证码或证件照片。";
        }
        return "SkyBooker 支持订票、退票、改签、候补、乘机人和订单相关流程。请告诉我您想了解哪个流程，我会按平台固定规则说明。";
    }

    private String compositionPrompt(DomainIntent intent) {
        return """
                你是 SkyBooker 航班与旅行助手。请用中文简洁回答用户。
                当前 intent 是 %s。回答必须限制在旅行、出行准备或航班查询引导范围内。
                不能编造具体航班号、价格、时刻、余票、座位数、可订状态、详情链接或预订链接。
                如果用户需要具体航班事实，引导其提供出发城市、目的地和出发日期，由后端数据库查询。
                """.formatted(intent.name());
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9._-]+", "sk-***");
    }
}
