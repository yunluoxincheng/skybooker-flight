package com.skybooker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.config.AiLlmProperties;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmConfigCrypto;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.mapper.AiLlmConfigMapper;
import com.skybooker.ai.parser.IntentParserService;
import com.skybooker.ai.service.DomainIntentRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class DomainIntentRouterTest {

    private AiLlmProperties properties;
    private ObjectMapper objectMapper;
    private IntentParserService ruleIntentParserService;

    @BeforeEach
    void setUp() {
        properties = new AiLlmProperties();
        properties.setEnabled(false);
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        objectMapper = new ObjectMapper();
        ruleIntentParserService = new IntentParserService();
    }

    @Test
    void route_commonDeterministicIntents() {
        DomainIntentRouter router = newRouter("{}");

        assertThat(router.route("你好", null).intent()).isEqualTo(DomainIntent.TRAVEL_CHAT);
        assertThat(router.route("北京有什么好玩", null).intent()).isEqualTo(DomainIntent.TRAVEL_CHAT);
        assertThat(router.route("退票怎么操作", null).intent()).isEqualTo(DomainIntent.BOOKING_HELP);
        assertThat(router.route("帮我查上海到北京机票", null).intent()).isEqualTo(DomainIntent.FLIGHT_QUERY);
        assertThat(router.route("帮我写代码", null).intent()).isEqualTo(DomainIntent.OUT_OF_SCOPE);
    }

    @Test
    void route_destinationBoundaries() {
        DomainIntentRouter router = newRouter("{}");

        assertThat(router.route("我想去北京", null).intent()).isEqualTo(DomainIntent.FLIGHT_QUERY);
        assertThat(router.route("去北京", null).intent()).isEqualTo(DomainIntent.FLIGHT_QUERY);
        assertThat(router.route("北京旅行推荐", null).intent()).isEqualTo(DomainIntent.TRAVEL_CHAT);
    }

    @Test
    void route_previousFollowUpShortReplyWins() {
        DomainIntentRouter router = newRouter("{}");
        AiChatMessage previous = new AiChatMessage();
        previous.setExtraJson("""
                {"replyType":"FOLLOW_UP","missingFields":["departureDate"],"parsedCondition":{"departureCity":"上海","arrivalCity":"北京"}}
                """);

        // 纯"明天"靠 parser 解析出 departureDate，无需文本长度启发式。
        assertThat(router.route("明天", previous).intent()).isEqualTo(DomainIntent.FLIGHT_QUERY_CONTINUATION);
    }

    @Test
    void route_previousFollowUpBareSlotFillerWins() {
        DomainIntentRouter router = newRouter("{}");
        AiChatMessage previous = new AiChatMessage();
        previous.setExtraJson("""
                {"replyType":"FOLLOW_UP","missingFields":["arrivalCity"],"parsedCondition":{"departureCity":"上海"}}
                """);

        // 纯城市名/乘客数词 parse 不出结构化字段，由 looksLikeSlotFiller 兜底为 continuation。
        assertThat(router.route("北京", previous).intent()).isEqualTo(DomainIntent.FLIGHT_QUERY_CONTINUATION);
        assertThat(router.route("两个人", previous).intent()).isEqualTo(DomainIntent.FLIGHT_QUERY_CONTINUATION);
    }

    @Test
    void route_previousFollowUpNaturalSlotFillWins() {
        DomainIntentRouter router = newRouter("{}");
        AiChatMessage previous = new AiChatMessage();
        previous.setExtraJson("""
                {"replyType":"FOLLOW_UP","missingFields":["departureDate"],"parsedCondition":{"departureCity":"上海","arrivalCity":"北京"}}
                """);

        assertThat(router.route("我想明天上午从广州出发", previous).intent())
                .isEqualTo(DomainIntent.FLIGHT_QUERY_CONTINUATION);
        assertThat(router.route("下周一上午走，经济舱，一个人", previous).intent())
                .isEqualTo(DomainIntent.FLIGHT_QUERY_CONTINUATION);
    }

    @Test
    void route_previousFollowUpExplicitNonSearchStillWins() {
        DomainIntentRouter router = newRouter("{}");
        AiChatMessage previous = new AiChatMessage();
        previous.setExtraJson("""
                {"replyType":"FOLLOW_UP","missingFields":["departureDate"],"parsedCondition":{"departureCity":"上海","arrivalCity":"北京"}}
                """);

        assertThat(router.route("退票怎么操作", previous).intent()).isEqualTo(DomainIntent.BOOKING_HELP);
        assertThat(router.route("帮我写代码", previous).intent()).isEqualTo(DomainIntent.OUT_OF_SCOPE);
    }

    @Test
    void route_llmCanClassifyUnclearTravelIntent() {
        properties.setEnabled(true);
        DomainIntentRouter router = newRouter("{\"intent\":\"TRAVEL_CHAT\"}");

        DomainIntentRouter.RouteResult result = router.route("帮我安排一个放松计划", null);

        assertThat(result.intent()).isEqualTo(DomainIntent.TRAVEL_CHAT);
        assertThat(result.llmClassified()).isTrue();
    }

    @Test
    void route_llmFailureFallsBackToOutOfScope() {
        properties.setEnabled(true);
        DomainIntentRouter router = new DomainIntentRouter(providerFromProperties(properties), (system, user, cfg) -> {
            throw new RuntimeException("timeout Bearer sk-secret");
        }, objectMapper, ruleIntentParserService);

        assertThat(router.route("帮我安排一个放松计划", null).intent()).isEqualTo(DomainIntent.OUT_OF_SCOPE);
    }

    private DomainIntentRouter newRouter(String llmResponse) {
        return new DomainIntentRouter(providerFromProperties(properties), (system, user, cfg) -> llmResponse,
                objectMapper, ruleIntentParserService);
    }

    private DynamicLlmConfigProvider providerFromProperties(AiLlmProperties props) {
        AiLlmConfigMapper mapper = Mockito.mock(AiLlmConfigMapper.class);
        Mockito.when(mapper.findActive()).thenReturn(null);
        LlmConfigCrypto crypto = new LlmConfigCrypto("");
        return new DynamicLlmConfigProvider(mapper, props, crypto);
    }
}
