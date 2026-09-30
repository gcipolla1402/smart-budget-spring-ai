package dio.budgeting.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigurationTest {

    @Test
    void should_useSaoPauloZone_when_creatingClock() {
        var clock = new ClockConfiguration().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
