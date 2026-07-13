package com.skybooker.ai;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.AiReplyComposer;
import com.skybooker.ai.service.FlightConditionSummaryFormatter;
import com.skybooker.ai.tool.FlightMatchLevel;
import com.skybooker.ai.tool.FlightSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiReplyComposerTest {

    private AiReplyComposer composer;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("UTC"));
        composer = new AiReplyComposer(new FlightConditionSummaryFormatter(clock));
    }

    @Test
    void exactReplyIncludesRouteDateAndCount() {
        ParsedCondition condition = baseCondition().build();
        String reply = composer.composeSearchReply(condition, condition,
                result(FlightMatchLevel.EXACT, appliedBase(), List.of(), 3),
                "明天广州飞北京", false);

        assertThat(reply).contains("3 个", "广州飞往北京", "明天出发").doesNotContain("为你");
    }

    @Test
    void continuationConfirmsUpdatedTimeAndPreservedConditions() {
        ParsedCondition active = baseCondition()
                .departureTimeStart(LocalTime.NOON).departureTimeEnd(LocalTime.of(18, 0)).build();
        ParsedCondition explicit = ParsedCondition.builder()
                .departureTimeStart(LocalTime.NOON).departureTimeEnd(LocalTime.of(18, 0)).build();

        String reply = composer.composeSearchReply(active, explicit,
                result(FlightMatchLevel.EXACT, Map.of("departureDate", "2026-07-14"), List.of(), 1),
                "下午的呢", true);

        assertThat(reply).startsWith("好的，已将起飞时间改为下午，其他未提及条件保持不变。");
    }

    @Test
    void timeClearIsConfirmedWithoutClaimingOtherChanges() {
        String reply = composer.composeSearchReply(baseCondition().build(), ParsedCondition.builder().build(),
                result(FlightMatchLevel.EXACT, appliedBase(), List.of(), 1),
                "时间不限", true);

        assertThat(reply).startsWith("好的，已取消起飞时段限制，其他未提及条件保持不变。");
    }

    @Test
    void timeClearAndDestinationUpdateAreBothConfirmed() {
        ParsedCondition requested = baseCondition().arrivalCity("上海").build();
        ParsedCondition explicit = ParsedCondition.builder().arrivalCity("上海").build();
        String reply = composer.composeSearchReply(requested, explicit,
                result(FlightMatchLevel.EXACT, Map.of(
                        "departureCity", "广州", "arrivalCity", "上海", "departureDate", "2026-07-14"),
                        List.of(), 1), "时间不限，目的地改成上海", true);

        assertThat(reply).startsWith("好的，已取消起飞时段限制，已将目的地改为上海，")
                .contains("其他未提及条件保持不变");
    }

    @Test
    void relaxedReplyNamesRelaxedFields() {
        ParsedCondition condition = baseCondition().airlineRaw("南方航空").cabinClass("ECONOMY").build();
        String reply = composer.composeSearchReply(condition, condition,
                result(FlightMatchLevel.RELAXED, appliedBase(),
                        List.of("airlineRaw", "cabinClass"), 4), "查询", false);

        assertThat(reply).contains("“航空公司”", "“舱位”", "保留了广州飞往北京、明天出发", "4 个");
    }

    @Test
    void partialReplyDistinguishesRequestedAndAppliedDates() {
        String reply = composer.composeSearchReply(baseCondition().build(), baseCondition().build(),
                result(FlightMatchLevel.PARTIAL, Map.of("departureDate", "2026-07-15"),
                        List.of("departureDate"), 2), "查询", false);

        assertThat(reply).contains("明天暂时没有广州飞往北京", "最近有航班的日期是 7 月 15 日")
                .doesNotContain("符合条件");
    }

    @Test
    void fallbackExplicitlyWarnsThatResultsDoNotMatch() {
        String reply = composer.composeSearchReply(baseCondition().build(), baseCondition().build(),
                result(FlightMatchLevel.FALLBACK, Map.of("departureDate", "2026-07-15"), List.of(), 2),
                "查询", false);

        assertThat(reply).contains("仅供参考", "与您刚才的查询条件不完全一致");
    }

    @Test
    void missingFieldQuestionOnlyAsksForMissingField() {
        ParsedCondition condition = ParsedCondition.builder().arrivalCity("北京")
                .departureDate(LocalDate.of(2026, 7, 14)).missingFields(List.of("departureCity")).build();

        assertThat(composer.composeMissingFieldsReply(condition, condition, "明天去北京", false))
                .contains("明天", "飞往北京", "请问从哪个城市出发？")
                .doesNotContain("哪天出发");
    }

    @Test
    void defaultDateIsAnnounced() {
        ParsedCondition condition = ParsedCondition.builder()
                .departureCity("广州").arrivalCity("北京").build();
        String reply = composer.composeSearchReply(condition, condition,
                result(FlightMatchLevel.EXACT, Map.of(
                        "departureCity", "广州", "arrivalCity", "北京", "departureDate", "2026-07-13"),
                        List.of(), 1),
                "广州到北京", false);

        assertThat(reply).contains("您还没有指定日期，我先按今天查询");
    }

    @Test
    void resetReplyIsNatural() {
        assertThat(composer.composeResetReply()).isEqualTo("已清空当前航班查询条件，我们可以重新开始。");
    }

    private ParsedCondition.ParsedConditionBuilder baseCondition() {
        return ParsedCondition.builder().departureCity("广州").arrivalCity("北京")
                .departureDate(LocalDate.of(2026, 7, 14)).passengerCount(1);
    }

    private FlightSearchResult result(FlightMatchLevel level, Map<String, Object> applied,
                                      List<String> relaxed, int count) {
        List<Map<String, Object>> flights = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> Map.<String, Object>of("id", index + 1)).toList();
        return new FlightSearchResult(flights, "/flights", level, applied, relaxed, "");
    }

    private Map<String, Object> appliedBase() {
        return Map.of("departureCity", "广州", "arrivalCity", "北京", "departureDate", "2026-07-14");
    }
}
