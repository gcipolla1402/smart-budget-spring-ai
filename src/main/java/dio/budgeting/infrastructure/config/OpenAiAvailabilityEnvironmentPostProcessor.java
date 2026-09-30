package dio.budgeting.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

public class OpenAiAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String OPENAI_API_KEY_PROPERTY = "spring.ai.openai.api-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty(OPENAI_API_KEY_PROPERTY))) {
            return;
        }

        environment.getPropertySources().addFirst(new MapPropertySource("openAiDisabled", Map.of(
                "spring.ai.chat.client.enabled", "false",
                "spring.ai.model.chat", "none",
                "spring.ai.model.embedding", "none",
                "spring.ai.model.image", "none",
                "spring.ai.model.moderation", "none",
                "spring.ai.model.audio.transcription", "none",
                "spring.ai.model.audio.speech", "none"
        )));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
