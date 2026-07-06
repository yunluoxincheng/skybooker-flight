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
        parser.setClock(fixedClock(LocalDate.of(2026, 6, 9)));
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

    @Test
    void parse_supportedSingleDayDateExpressions_resolveDeterministically() {
        parser.setClock(fixedClock(LocalDate.of(2026, 7, 2)));

        assertThat(parser.parse("上海到北京2026-07-06机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(parser.parse("上海到北京2026年7月6日机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(parser.parse("上海到北京7月6日机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(parser.parse("上海到北京今天机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(parser.parse("上海到北京明天机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(parser.parse("上海到北京后天机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(parser.parse("上海到北京大后天机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(parser.parse("上海到北京周五机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(parser.parse("上海到北京这周五机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(parser.parse("上海到北京本周五机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(parser.parse("上海到北京下周一机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(parser.parse("上海到北京下星期一机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(parser.parse("上海到北京下个周一机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(parser.parse("上海到北京周末机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(parser.parse("上海到北京下周末机票").getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 11));
    }

    @Test
    void parse_monthDayBeforeCurrentDate_rollsToNextYear() {
        parser.setClock(fixedClock(LocalDate.of(2026, 7, 2)));

        ParsedCondition result = parser.parse("上海到北京6月30日机票");

        assertThat(result.getDepartureDate()).isEqualTo(LocalDate.of(2027, 6, 30));
    }

    @Test
    void parse_chineseCityRouteWithoutFromKeyword() {
        parser.setClock(fixedClock(LocalDate.of(2026, 7, 2)));

        ParsedCondition result = parser.parse("上海到北京下周一机票");

        assertThat(result.getDepartureCity()).isEqualTo("上海");
        assertThat(result.getArrivalCity()).isEqualTo("北京");
        assertThat(result.getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 6));
    }

    @Test
    void parse_destinationOnlyPhrase_setsArrivalCityAndAsksForMissingFields() {
        ParsedCondition result = parser.parse("你好，我想去上海迪士尼玩");

        assertThat(result.getArrivalCity()).isEqualTo("上海");
        assertThat(result.getMissingFields()).contains("departureCity", "departureDate");
        assertThat(result.getDepartureDate()).isNull();
    }

    @Test
    void parse_departureCityWithoutFromRequiresKnownCity() {
        ParsedCondition departure = parser.parse("那就去三亚吧，广州出发，下周五");
        assertThat(departure.getDepartureCity()).isEqualTo("广州");

        ParsedCondition dateOnly = parser.parse("明天出发");
        assertThat(dateOnly.getDepartureCity()).isNull();
        assertThat(dateOnly.getDepartureDate()).isEqualTo(LocalDate.of(2026, 6, 10));
    }

    @Test
    void parseDestinationSwitchCity_prefersReplacementCity() {
        assertThat(parser.parseDestinationSwitchCity("不要三亚，换成厦门")).isEqualTo("厦门");
        assertThat(parser.parseDestinationSwitchCity("目的地改成青岛吧")).isEqualTo("青岛");
    }

    @Test
    void parse_priceCeilingPhrase_setsMaxPrice() {
        ParsedCondition result = parser.parse("上海到北京明天，两个人，经济舱，直飞，1000以内");

        assertThat(result.getMaxPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void parse_supportedDateRanges_areCompleteDateConditions() {
        parser.setClock(fixedClock(LocalDate.of(2026, 7, 2)));

        ParsedCondition explicitRange = parser.parse("上海到北京7月6日到7月8日机票");
        assertThat(explicitRange.getDepartureDate()).isNull();
        assertThat(explicitRange.getDepartureDateStart()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(explicitRange.getDepartureDateEnd()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(explicitRange.getMissingFields()).doesNotContain("departureDate");
        assertThat(explicitRange.isComplete()).isTrue();

        ParsedCondition contextualRange = parser.parse("我现在在广州，打算八月初去，大概2到5号这几天的机票");
        assertThat(contextualRange.getDepartureDateStart()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(contextualRange.getDepartureDateEnd()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(contextualRange.getDepartureCity()).isEqualTo("广州");
        assertThat(contextualRange.getMissingFields()).doesNotContain("departureDate");
    }

    @Test
    void parseFirstKnownDestinationCity_usesFirstMentionedRecommendation() {
        String text = "推荐旅游目的地取决于您的偏好和季节。如果您喜欢海滩，可以考虑三亚、青岛；钟情历史，西安、南京很值得。";

        assertThat(parser.parseFirstKnownDestinationCity(text)).isEqualTo("三亚");
    }

    @Test
    void parseFirstKnownDestinationCity_skipsDepartureCityCue() {
        String text = "如果从广州出发，可以考虑三亚、青岛这类海滨目的地。";

        assertThat(parser.parseFirstKnownDestinationCity(text)).isEqualTo("三亚");
    }

    @Test
    void parse_ambiguousDateRange_asksForSpecificDepartureDate() {
        parser.setClock(fixedClock(LocalDate.of(2026, 7, 2)));

        for (String message : java.util.List.of("最近几天的机票", "未来一周机票", "周一周二都可以")) {
            ParsedCondition result = parser.parse("上海到北京" + message);

            assertThat(result.getDepartureDate()).as(message).isNull();
            assertThat(result.getMissingFields()).as(message).contains("departureDate");
            assertThat(result.getFollowUpQuestion()).as(message).contains("一个出发日期");
        }
    }

    private Clock fixedClock(LocalDate date) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        return Clock.fixed(date.atStartOfDay(zone).toInstant(), zone);
    }
}
