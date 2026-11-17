package com.miqueiasbento.io.webhook_service.exception;

import lombok.Getter;

@Getter
public abstract class AppllicationException extends RuntimeException {
    private final String field;

    public AppllicationException(String message, String field) {
        super(message);
        this.field = field;
    }
}
