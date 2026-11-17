package com.miqueiasbento.io.webhook_service.exception;

import com.miqueiasbento.io.webhook_service.exception.dto.ErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getField(), ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> status(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, "request", ex.getReason() == null ? status.getReasonPhrase() : ex.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> validation(MethodArgumentNotValidException ex) {
        var fieldError = ex.getBindingResult().getFieldError();
        return error(HttpStatus.BAD_REQUEST, fieldError == null ? "request" : fieldError.getField(),
                fieldError == null ? "Invalid request" : fieldError.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> malformedBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "body", "Malformed or missing request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> invalidPath(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getName(), "Invalid parameter value");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> unknownRoute(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "path", "Route not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> unsupportedMethod(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "method", "HTTP method not supported");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "contentType", "Media type not supported");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> unexpected(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "request", "Unexpected server error");
    }

    private ResponseEntity<ErrorResponseDTO> error(HttpStatus status, String field, String message) {
        return ResponseEntity.status(status).body(new ErrorResponseDTO(
                field, message, status.value(), status.getReasonPhrase()));
    }
}
