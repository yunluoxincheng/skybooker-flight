package com.skybooker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.config.AiLlmProperties;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmConfigCrypto;
import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.mapper.AiLlmConfigMapper;
import com.skybooker.ai.parser.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LlmIntentParserServiceTest {

    private ObjectMapper objectMapper;
    private IntentParserService ruleParser;
    private AiLlmProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ruleParser = new IntentParserService();
        properties = new AiLlmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");
    }

    @Test
    void parse_validProviderJson_normalizesCurrentContractFields() {
        LlmIntentParserService parser = parserReturning("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "2026-06-12",
                  "passengerCount": 3,
                  "cabinClass": "economy",
                  "airlineRaw": "南方航空",
                  "minPrice": 300,
                  "maxPrice": "1200.50",
                  "departureTimeStart": "08:00",
                  "departureTimeEnd": "12:00",
                  "maxDurationMinutes": 180,
                  "directOnly": true,
                  "sort": "price_asc",
                  "quickActionLabels": ["查看详情", "换个时间"],
                  "flightNo": "FAKE123",
                  "price": 1,
                  "bookingUrl": "/fake"
                }
                """);

        var result = parser.parse("帮我查机票", cfg());

        assertThat(result.getDepartureCity()).isEqualTo("上海");
        assertThat(result.getArrivalCity()).isEqualTo("北京");
        assertThat(result.getDepartureDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(result.getPassengerCount()).isEqualTo(3);
        assertThat(result.getCabinClass()).isEqualTo("ECONOMY");
        assertThat(result.getAirlineRaw()).isEqualTo("南方航空");
        assertThat(result.getMinPrice()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(result.getMaxPrice()).isEqualByComparingTo(new BigDecimal("1200.50"));
        assertThat(result.getDepartureTimeStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.getDepartureTimeEnd()).isEqualTo(LocalTime.of(12, 0));
        assertThat(result.getMaxDurationMinutes()).isEqualTo(180);
        assertThat(result.getDirectOnly()).isTrue();
        assertThat(result.getSort()).isEqualTo("PRICE_ASC");
        assertThat(result.getQuickActionLabels()).containsExactly("查看详情", "换个时间");
        assertThat(result.getMissingFields()).isEmpty();
    }

    @Test
    void parse_missingRequiredFields_returnsFollowUpData() {
        LlmIntentParserService parser = parserReturning("""
                {
                  "departureCity": "上海",
                  "missingFields": ["arrivalCity", "departureDate"],
                  "followUpQuestion": "请问您要飞往哪里，哪天出发？"
                }
                """);

        var result = parser.parse("上海出发", cfg());

        assertThat(result.isComplete()).isFalse();
        assertThat(result.getMissingFields()).containsExactly("arrivalCity", "departureDate");
        assertThat(result.getFollowUpQuestion()).isEqualTo("请问您要飞往哪里，哪天出发？");
    }

    @Test
    void parse_missingPassengerCount_keepsNullUntilSearchNormalization() {
        LlmIntentParserService parser = parserReturning("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "2026-06-12"
                }
                """);

        var result = parser.parse("上海到北京机票", cfg());

        assertThat(result.getPassengerCount()).isNull();
    }

    @Test
    void parse_systemPromptIncludesCurrentDateForRelativeDateResolution() {
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>();
        LlmIntentParserService parser = new LlmIntentParserService((system, user, cfg) -> {
            capturedSystemPrompt.set(system);
            return """
                    {
                      "departureCity": "上海",
                      "arrivalCity": "北京",
                      "departureDate": "2026-06-13"
                    }
                    """;
        }, objectMapper);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        parser.setClock(Clock.fixed(LocalDate.of(2026, 6, 12).atStartOfDay(zone).toInstant(), zone));

        parser.parse("明天从上海到北京", cfg());

        assertThat(capturedSystemPrompt.get())
                .contains("当前日期是 2026-06-12")
                .contains("今天、明天、后天")
                .contains("必须基于这个日期换算");
    }

    @Test
    void composite_disabledConfiguration_usesRuleParserWithoutCallingLlm() {
        properties.setEnabled(false);
        AtomicInteger calls = new AtomicInteger();
        LlmIntentParserService llmParser = new LlmIntentParserService((system, user, cfg) -> {
            calls.incrementAndGet();
            return "{}";
        }, objectMapper);
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天出发");

        assertThat(result.getDepartureCity()).isEqualTo("上海");
        assertThat(result.getArrivalCity()).isEqualTo("北京");
        assertThat(calls).hasValue(0);
    }

    @Test
    void composite_missingConfiguration_usesRuleParserWithoutCallingLlm() {
        properties.setApiKey("");
        AtomicInteger calls = new AtomicInteger();
        LlmIntentParserService llmParser = new LlmIntentParserService((system, user, cfg) -> {
            calls.incrementAndGet();
            return "{}";
        }, objectMapper);
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天出发");

        assertThat(result.getDepartureCity()).isEqualTo("上海");
        assertThat(result.getArrivalCity()).isEqualTo("北京");
        assertThat(calls).hasValue(0);
    }

    @Test
    void composite_providerFailure_fallsBackToRuleParser() {
        LlmIntentParserService llmParser = new LlmIntentParserService((system, user, cfg) -> {
            throw new LlmIntentParseException("timeout");
        }, objectMapper);
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天出发");

        assertThat(result.getDepartureCity()).isEqualTo("上海");
        assertThat(result.getArrivalCity()).isEqualTo("北京");
        assertThat(result.getDepartureDate()).isNotNull();
    }

    @Test
    void composite_malformedJson_fallsBackToRuleParser() {
        LlmIntentParserService llmParser = parserReturning("not json");
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天出发");

        assertThat(result.getDepartureCity()).isEqualTo("上海");
        assertThat(result.getArrivalCity()).isEqualTo("北京");
    }

    @Test
    void composite_unsupportedEnum_fallsBackToRuleParser() {
        LlmIntentParserService llmParser = parserReturning("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "2026-06-12",
                  "sort": "MAGIC"
                }
                """);
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天便宜");

        assertThat(result.getSort()).isEqualTo("PRICE_ASC");
    }

    @Test
    void composite_invalidDate_fallsBackToRuleParser() {
        LlmIntentParserService llmParser = parserReturning("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "2026-02-30"
                }
                """);
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天出发");

        assertThat(result.getDepartureDate()).isNotNull();
    }

    @Test
    void composite_outOfRangePassengerCount_fallsBackToRuleParser() {
        LlmIntentParserService llmParser = parserReturning("""
                {
                  "departureCity": "上海",
                  "arrivalCity": "北京",
                  "departureDate": "2026-06-12",
                  "passengerCount": 99
                }
                """);
        CompositeIntentParser composite = new CompositeIntentParser(providerFromProperties(properties), llmParser, ruleParser);

        var result = composite.parse("从上海到北京明天 2人");

        assertThat(result.getPassengerCount()).isEqualTo(2);
    }

    private LlmIntentParserService parserReturning(String content) {
        return new LlmIntentParserService((system, user, cfg) -> content, objectMapper);
    }

    /** 单测用配置快照（字段值不影响 parse 的 JSON 解析逻辑，仅用于满足方法签名）。 */
    private LlmEffectiveConfig cfg() {
        return new LlmEffectiveConfig(true, "https://example.test/v1", "test-key", "test-model", 8000, 1, "test");
    }

    /** 构造一个 DB 无记录、直接 fallback 到给定 properties 的 provider（用于单测 CompositeIntentParser）。 */
    private DynamicLlmConfigProvider providerFromProperties(AiLlmProperties props) {
        AiLlmConfigMapper mapper = Mockito.mock(AiLlmConfigMapper.class);
        Mockito.when(mapper.findActive()).thenReturn(null);
        LlmConfigCrypto crypto = new LlmConfigCrypto("");
        return new DynamicLlmConfigProvider(mapper, props, crypto);
    }
}
