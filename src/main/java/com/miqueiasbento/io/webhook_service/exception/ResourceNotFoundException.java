package com.miqueiasbento.io.webhook_service.exception;

public class ResourceNotFoundException extends AppllicationException {
    public ResourceNotFoundException() {
        super("resource not found", "resource");
    }

    public ResourceNotFoundException(String message, String field) {
        super(message, field);
    }
}
