package com.example.connectong_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao da documentacao da API (Swagger UI / OpenAPI).
 * Acesse em: http://localhost:8080/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI connectOngOpenAPI() {
        return new OpenAPI().info(
                new Info()
                        .title("Connect ONG API")
                        .description(
                                "API REST da plataforma Connect ONG — "
                                        + "intermediacao de doacoes entre doadores e ONGs.")
                        .version("v1"));
    }
}
