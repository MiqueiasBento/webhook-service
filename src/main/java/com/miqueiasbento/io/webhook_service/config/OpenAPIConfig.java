package com.miqueiasbento.io.webhook_service.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER, paramName = "X-API-Key"
)
@OpenAPIDefinition(
        info = @Info(
                title = "Webhook Service API",
                version = "1.0.0",
                summary = "API para o serviço de webhook"
        )
)
public class OpenAPIConfig {
}
