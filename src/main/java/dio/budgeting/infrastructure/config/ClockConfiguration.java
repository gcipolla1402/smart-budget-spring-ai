package dio.budgeting.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfiguration {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
