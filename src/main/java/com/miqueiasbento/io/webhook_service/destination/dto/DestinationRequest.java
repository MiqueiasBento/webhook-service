package com.miqueiasbento.io.webhook_service.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DestinationRequest(
        @NotBlank 
        String name,

        @NotBlank 
        @Pattern(regexp = "https?://.+", message = "must be an HTTP or HTTPS URL") 
        String url
) {
}
