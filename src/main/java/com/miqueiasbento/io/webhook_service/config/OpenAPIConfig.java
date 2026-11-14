package com.miqueiasbento.io.webhook_service.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Webhook Service API",
                version = "1.0.0",
                summary = "API para o serviço de webhook"
        )
)
public class OpenAPIConfig {
}
