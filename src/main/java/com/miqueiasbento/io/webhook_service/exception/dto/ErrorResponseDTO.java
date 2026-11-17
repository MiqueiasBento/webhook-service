package com.miqueiasbento.io.webhook_service.exception.dto;

public record ErrorResponseDTO(
    String field,
    String message,
    Integer statusCode,
    String error) {
}
