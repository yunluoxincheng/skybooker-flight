package com.skybooker.flight;

import com.skybooker.common.exception.BusinessException;
import com.skybooker.flight.service.CityQueryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CityQueryServiceTest {

    private final CityQueryService service = new CityQueryService();

    @Test
    void normalizesWhitespaceAndOneCitySuffixIntoBoundedCandidates() {
        assertThat(service.normalizeRequired("  上海市  ")).isEqualTo("上海");
        assertThat(service.candidates(service.normalizeRequired("上海")))
                .containsExactly("上海", "上海市");
    }

    @Test
    void rejectsBlankRequiredCity() {
        assertThatThrownBy(() -> service.normalizeRequired("　 "))
                .isInstanceOf(BusinessException.class);
    }
}
