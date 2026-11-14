package com.miqueiasbento.io.webhook_service.delivery;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventRequest(
        @NotNull Long destinationId,
        @NotBlank String eventType,
        @NotNull JsonNode payload) {
}
