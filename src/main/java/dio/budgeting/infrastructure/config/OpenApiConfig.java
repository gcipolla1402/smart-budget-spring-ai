package dio.budgeting.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI budgetingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Budgeting API")
                .version("1.0.0")
                .description("API financeira para cadastrar e consultar despesas e, quando configurada, "
                        + "interpretar comandos enviados por áudio com Inteligência Artificial."));
    }
}
