package com.skybooker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.config.AiLlmProperties;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmConfigCrypto;
import com.skybooker.ai.entity.AiChatMessage;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.mapper.AiLlmConfigMapper;
import com.skybooker.ai.service.DomainIntentRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class DomainIntentRouterTest {

    private AiLlmProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new AiLlmProperties();
        properties.setEnabled(false);
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        objectMapper = new ObjectMapper();
    }

    @Test
    void route_commonDeterministicIntents() {
        DomainIntentRouter router = newRouter("{}");

        assertThat(router.route("你好", null).intent()).isEqualTo(DomainIntent.GREETING);
        assertThat(router.route("北京有什么好玩", null).intent()).isEqualTo(DomainIntent.TRAVEL_ADVICE);
        assertThat(router.route("退票怎么操作", null).intent()).isEqualTo(DomainIntent.PLATFORM_HELP);
        assertThat(router.route("帮我查上海到北京机票", null).intent()).isEqualTo(DomainIntent.FLIGHT_SEARCH);
        assertThat(router.route("帮我写代码", null).intent()).isEqualTo(DomainIntent.OUT_OF_SCOPE);
    }

    @Test
    void route_destinationBoundaries() {
        DomainIntentRouter router = newRouter("{}");

        assertThat(router.route("我想去北京", null).intent()).isEqualTo(DomainIntent.FLIGHT_SEARCH);
        assertThat(router.route("去北京", null).intent()).isEqualTo(DomainIntent.FLIGHT_SEARCH);
        assertThat(router.route("北京旅行推荐", null).intent()).isEqualTo(DomainIntent.TRAVEL_ADVICE);
    }

    @Test
    void route_previousFollowUpShortReplyWins() {
        DomainIntentRouter router = newRouter("{}");
        AiChatMessage previous = new AiChatMessage();
        previous.setExtraJson("""
                {"replyType":"FOLLOW_UP","missingFields":["departureDate"],"parsedCondition":{"departureCity":"上海","arrivalCity":"北京"}}
                """);

        assertThat(router.route("明天", previous).intent()).isEqualTo(DomainIntent.FLIGHT_SEARCH_CONTINUATION);
    }

    @Test
    void route_llmCanClassifyUnclearTravelIntent() {
        properties.setEnabled(true);
        DomainIntentRouter router = newRouter("{\"intent\":\"TRAVEL_ADVICE\"}");

        DomainIntentRouter.RouteResult result = router.route("帮我安排一个放松计划", null);

        assertThat(result.intent()).isEqualTo(DomainIntent.TRAVEL_ADVICE);
        assertThat(result.llmClassified()).isTrue();
    }

    @Test
    void route_llmFailureFallsBackToOutOfScope() {
        properties.setEnabled(true);
        DomainIntentRouter router = new DomainIntentRouter(providerFromProperties(properties), (system, user, cfg) -> {
            throw new RuntimeException("timeout Bearer sk-secret");
        }, objectMapper);

        assertThat(router.route("帮我安排一个放松计划", null).intent()).isEqualTo(DomainIntent.OUT_OF_SCOPE);
    }

    private DomainIntentRouter newRouter(String llmResponse) {
        return new DomainIntentRouter(providerFromProperties(properties), (system, user, cfg) -> llmResponse, objectMapper);
    }

    private DynamicLlmConfigProvider providerFromProperties(AiLlmProperties props) {
        AiLlmConfigMapper mapper = Mockito.mock(AiLlmConfigMapper.class);
        Mockito.when(mapper.findActive()).thenReturn(null);
        LlmConfigCrypto crypto = new LlmConfigCrypto("");
        return new DynamicLlmConfigProvider(mapper, props, crypto);
    }
}
