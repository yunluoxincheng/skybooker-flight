package com.skybooker.ai;

import com.skybooker.ai.service.DomainReplyComposer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainReplyComposerTest {

    private final DomainReplyComposer composer = new DomainReplyComposer((system, user, cfg) -> "");

    @Test
    void containsConcreteFlightFactsRejectsSearchUrls() {
        assertThat(composer.containsConcreteFlightFacts("/flights?departureCity=上海&arrivalCity=北京"))
                .isTrue();
        assertThat(composer.containsConcreteFlightFacts("/flights/1")).isTrue();
        assertThat(composer.containsConcreteFlightFacts("/booking/1")).isTrue();
    }
}
