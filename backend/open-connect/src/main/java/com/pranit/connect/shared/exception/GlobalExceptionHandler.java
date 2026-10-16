package com.pranit.connect.shared.exception;

import com.pranit.connect.shared.wrapper.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled
            (final DisabledException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Account is not active. Please verify your email.", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse
            (final HttpStatus status, final String message, final HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .message(message)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials
            (final BadCredentialsException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationAccess
            (final AuthorizationDeniedException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied. You are not authorized to perform this operation.", request);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(final BaseException ex, final HttpServletRequest request) {
        final ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        final HttpStatus status = responseStatus != null
                ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        return buildResponse(status, ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(final UsernameNotFoundException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize
            (final MaxUploadSizeExceededException ex, final HttpServletRequest request) {
        log.warn("File size limit exceeded: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_CONTENT, "File size exceeds the allowed limit (25MB).", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation
            (final MethodArgumentNotValidException ex, final HttpServletRequest request) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return buildValidationResponse(errors, request);
    }

    private ResponseEntity<ErrorResponse> buildValidationResponse
            (final Map<String, String> errors, final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .message("Validation failed.")
                        .errors(errors)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported
            (final HttpRequestMethodNotSupportedException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument
            (final IllegalArgumentException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException
            (final RuntimeException ex, final HttpServletRequest request) {
        log.error("Unhandled runtime exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException
            (final Exception ex, final HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
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

}