package com.pranit.connect.exception;

import com.pranit.connect.wrapper.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global Exception Handler for both HTTP REST and WebSocket STOMP messaging.
 * <p>
 * Annotated with {@link RestControllerAdvice}, which Spring Framework uses across both
 * HTTP DispatcherServlet requests and STOMP message broker channels.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            final BaseException ex, final HttpServletRequest request) {
        final ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        final HttpStatus status = responseStatus != null
                ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        return buildResponse(status, ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            final HttpStatus status, final String message, final HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .message(message)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            final MethodArgumentNotValidException ex, final HttpServletRequest request) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return buildValidationResponse(errors, request);
    }

    private ResponseEntity<ErrorResponse> buildValidationResponse(
            final Map<String, String> errors, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .message("Validation failed.")
                        .errors(errors)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            final ConstraintViolationException ex, final HttpServletRequest request) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            int dotIdx = propertyPath.lastIndexOf('.');
            String field = dotIdx >= 0 ? propertyPath.substring(dotIdx + 1) : propertyPath;
            errors.put(field, violation.getMessage());
        }
        return buildValidationResponse(errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            final HandlerMethodValidationException ex, final HttpServletRequest request) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final var error : ex.getAllErrors()) {
            errors.put("parameter", error.getDefaultMessage());
        }
        return buildValidationResponse(errors, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            final HttpRequestMethodNotSupportedException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            final IllegalArgumentException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            final NoResourceFoundException ex, final HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/swagger-ui/")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/v3/api-docs")) {
            return buildResponse(HttpStatus.NOT_FOUND, "Swagger resource not found.", request);
        }
        return buildResponse(HttpStatus.NOT_FOUND, "The requested resource was not found.", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            final RuntimeException ex, final HttpServletRequest request) {
        log.error("Unhandled runtime exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            final Exception ex, final HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }


    /**
     * Handles STOMP message payload validation errors (@Valid failures).
     * Dispatches the ErrorResponse to the offending user's private queue (/user/queue/errors).
     */
    @MessageExceptionHandler(org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleWebSocketValidation(
            final org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException ex) {
        final Map<String, String> errors = new LinkedHashMap<>();
        if (ex.getBindingResult() != null) {
            for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
        }
        return ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("WebSocket message validation failed.")
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Handles constraint violation exceptions on destination variables or method parameters.
     */
    @MessageExceptionHandler(ConstraintViolationException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleWebSocketConstraintViolation(final ConstraintViolationException ex) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("WebSocket destination constraint validation failed.")
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Handles business domain exceptions over WebSocket.
     */
    @MessageExceptionHandler(BaseException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleWebSocketBaseException(final BaseException ex) {
        return ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Fallback exception handler for unexpected WebSocket errors.
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleWebSocketGeneralException(final Exception ex) {
        log.error("Unhandled WebSocket exception", ex);
        return ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An error occurred processing the WebSocket message.")
                .timestamp(Instant.now())
                .build();
    }
}