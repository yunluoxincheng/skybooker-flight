package com.skybooker.ai;

import com.skybooker.ai.parser.ParsedCondition;
import com.skybooker.ai.service.FlightRecommendationService;
import com.skybooker.ai.tool.FlightMatchLevel;
import com.skybooker.ai.tool.FlightSearchResult;
import com.skybooker.ai.tool.FlightSearchTool;
import com.skybooker.flight.mapper.FlightMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlightSearchFallbackTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-13T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void defaultsMissingDateToBusinessTodayForPartialSearch() {
        FlightRecommendationService service = mock(FlightRecommendationService.class);
        when(service.recommend(any(), isNull())).thenReturn(List.of(Map.of("id", 1L)));
        when(service.buildSearchUrl(any(), isNull())).thenReturn("/flights?departureDate=2026-07-13");
        FlightSearchTool tool = new FlightSearchTool(service, mock(FlightMapper.class), CLOCK);

        FlightSearchResult result = tool.search(ParsedCondition.builder().arrivalCity("北京").build());

        ArgumentCaptor<ParsedCondition> captor = ArgumentCaptor.forClass(ParsedCondition.class);
        org.mockito.Mockito.verify(service).recommend(captor.capture(), isNull());
        assertThat(captor.getValue().getArrivalCity()).isEqualTo("北京");
        assertThat(captor.getValue().getDepartureCity()).isNull();
        assertThat(captor.getValue().getDepartureDate()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(result.matchLevel()).isEqualTo(FlightMatchLevel.EXACT);
    }

    @Test
    void relaxesSecondaryFiltersBeforeChangingCoreConditions() {
        FlightRecommendationService service = mock(FlightRecommendationService.class);
        when(service.recommend(any(), org.mockito.ArgumentMatchers.nullable(Long.class)))
                .thenReturn(List.of()).thenReturn(List.of(Map.of("id", 2L)));
        when(service.buildSearchUrl(any(), isNull())).thenReturn("/flights");
        FlightMapper mapper = mock(FlightMapper.class);
        when(mapper.findAirlineIdByCodeOrName("南方航空", "南方航空")).thenReturn(7L);
        FlightSearchTool tool = new FlightSearchTool(service, mapper, CLOCK);

        FlightSearchResult result = tool.search(ParsedCondition.builder()
                .departureCity("广州").arrivalCity("北京")
                .departureDate(LocalDate.of(2026, 7, 14)).airlineRaw("南方航空").build());

        assertThat(result.matchLevel()).isEqualTo(FlightMatchLevel.RELAXED);
        assertThat(result.relaxedFields()).containsExactly("airlineRaw");
        assertThat(result.appliedCondition()).containsEntry("departureCity", "广州")
                .containsEntry("arrivalCity", "北京")
                .doesNotContainKey("airlineRaw");
    }

    @Test
    void unrecognizedAirlineIsRelaxedInsteadOfReportedAsExact() {
        FlightRecommendationService service = mock(FlightRecommendationService.class);
        when(service.recommend(any(), isNull())).thenReturn(List.of(Map.of("id", 4L)));
        when(service.buildSearchUrl(any(), isNull())).thenReturn("/flights");
        FlightMapper mapper = mock(FlightMapper.class);
        when(mapper.findAirlineIdByCodeOrName("不存在航空", "不存在航空")).thenReturn(null);
        FlightSearchTool tool = new FlightSearchTool(service, mapper, CLOCK);

        FlightSearchResult result = tool.search(ParsedCondition.builder()
                .departureCity("广州").arrivalCity("北京")
                .departureDate(LocalDate.of(2026, 7, 14)).airlineRaw("不存在航空").build());

        assertThat(result.matchLevel()).isEqualTo(FlightMatchLevel.RELAXED);
        assertThat(result.relaxedFields()).containsExactly("airlineRaw");
        assertThat(result.appliedCondition()).doesNotContainKey("airlineRaw");
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(1)).recommend(any(), isNull());
    }

    @Test
    void relaxationKeepsSortAndDoesNotReportItAsRelaxed() {
        FlightRecommendationService service = mock(FlightRecommendationService.class);
        when(service.recommend(any(), org.mockito.ArgumentMatchers.nullable(Long.class)))
                .thenReturn(List.of()).thenReturn(List.of(Map.of("id", 5L)));
        when(service.buildSearchUrl(any(), org.mockito.ArgumentMatchers.nullable(Long.class)))
                .thenReturn("/flights");
        FlightMapper mapper = mock(FlightMapper.class);
        when(mapper.findAirlineIdByCodeOrName("南方航空", "南方航空")).thenReturn(7L);
        FlightSearchTool tool = new FlightSearchTool(service, mapper, CLOCK);

        FlightSearchResult result = tool.search(ParsedCondition.builder()
                .departureCity("广州").arrivalCity("北京").departureDate(LocalDate.of(2026, 7, 14))
                .airlineRaw("南方航空").sort("PRICE_ASC").build());

        ArgumentCaptor<ParsedCondition> captor = ArgumentCaptor.forClass(ParsedCondition.class);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(2))
                .recommend(captor.capture(), org.mockito.ArgumentMatchers.nullable(Long.class));
        assertThat(captor.getAllValues().get(1).getSort()).isEqualTo("PRICE_ASC");
        assertThat(result.appliedCondition()).containsEntry("sort", "PRICE_ASC");
        assertThat(result.relaxedFields()).containsExactly("airlineRaw");
    }

    @Test
    void explicitDateFallbackStartsAfterTheRequestedDate() {
        FlightRecommendationService service = mock(FlightRecommendationService.class);
        when(service.recommend(any(), isNull()))
                .thenReturn(List.of()).thenReturn(List.of(Map.of("id", 3L)));
        when(service.buildSearchUrl(any(), isNull())).thenReturn("/flights");
        FlightSearchTool tool = new FlightSearchTool(service, mock(FlightMapper.class), CLOCK);

        FlightSearchResult result = tool.search(ParsedCondition.builder()
                .departureCity("广州").arrivalCity("北京")
                .departureDate(LocalDate.of(2026, 7, 20)).build());

        assertThat(result.matchLevel()).isEqualTo(FlightMatchLevel.PARTIAL);
        assertThat(result.appliedCondition()).containsEntry("departureDate", "2026-07-21");
        assertThat(result.relaxedFields()).contains("departureDate");
    }
}
