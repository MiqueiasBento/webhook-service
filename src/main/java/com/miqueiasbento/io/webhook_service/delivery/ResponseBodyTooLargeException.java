package com.miqueiasbento.io.webhook_service.delivery;

public class ResponseBodyTooLargeException extends RuntimeException {
    private final int responseStatus;

    public ResponseBodyTooLargeException(int responseStatus) {
        super("Destination response exceeds the configured size limit");
        this.responseStatus = responseStatus;
    }

    public int getResponseStatus() {
        return responseStatus;
    }
}
