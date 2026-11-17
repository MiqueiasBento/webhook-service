package com.miqueiasbento.io.webhook_service.destination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

public record DestinationRequest(
        @NotBlank 
        String name,

        @NotBlank 
        @URL(message = "must be a valid URL")
        @Pattern(regexp = "https?://.+", message = "must be an HTTP or HTTPS URL") 
        String url
) {
}
