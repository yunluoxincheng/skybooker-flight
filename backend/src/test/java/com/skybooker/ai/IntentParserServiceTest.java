package com.skybooker.ai;

import com.skybooker.ai.parser.IntentParserService;
import com.skybooker.ai.parser.ParsedCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class IntentParserServiceTest {

    private IntentParserService parser;

    @BeforeEach
    void setUp() {
        parser = new IntentParserService();
        parser.setClock(Clock.fixed(LocalDate.of(2026, 6, 9).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void parse_invalidDate_returnsNullDepartureDate() {
        ParsedCondition result = parser.parse("从上海到北京 2026年02月30日出发");
        assertThat(result.getDepartureDate()).isNull();
    }

    @Test
    void parse_invalidShortDate_returnsNullDepartureDate() {
        ParsedCondition result = parser.parse("从上海到北京 2月30日出发");
        assertThat(result.getDepartureDate()).isNull();
    }

    @Test
    void parse_dayAfterTomorrow_correctDate() {
        ParsedCondition result = parser.parse("从上海到北京大后天出发");
        assertThat(result.getDepartureDate()).isEqualTo(LocalDate.of(2026, 6, 12));
    }

    @Test
    void parse_theDayAfterTomorrow_notConfusedWithTomorrow() {
        ParsedCondition result = parser.parse("大后天从上海到北京");
        assertThat(result.getDepartureDate()).isEqualTo(LocalDate.of(2026, 6, 12));
    }

    @Test
    void parse_durationInMinutes_notMultipliedBy60() {
        ParsedCondition result = parser.parse("从上海到北京明天出发 不超过90分钟");
        assertThat(result.getMaxDurationMinutes()).isEqualTo(90);
    }

    @Test
    void parse_durationInHours_correctlyMultiplied() {
        ParsedCondition result = parser.parse("从上海到北京明天出发 不超过2小时");
        assertThat(result.getMaxDurationMinutes()).isEqualTo(120);
    }

    @Test
    void parse_durationInMin_notMultipliedBy60() {
        ParsedCondition result = parser.parse("从上海到北京明天出发 最多120min");
        assertThat(result.getMaxDurationMinutes()).isEqualTo(120);
    }

    @Test
    void parse_validDate_works() {
        ParsedCondition result = parser.parse("从上海到北京 2026年06月15日出发");
        assertThat(result.getDepartureDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void parse_nullMessage_returnsIncomplete() {
        ParsedCondition result = parser.parse(null);
        assertThat(result.isComplete()).isFalse();
        assertThat(result.getMissingFields()).containsExactly("departureCity", "arrivalCity", "departureDate");
    }

    @Test
    void parse_blankMessage_returnsIncomplete() {
        ParsedCondition result = parser.parse("  ");
        assertThat(result.isComplete()).isFalse();
    }
}
