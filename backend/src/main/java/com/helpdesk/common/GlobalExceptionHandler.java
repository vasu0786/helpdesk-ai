package com.helpdesk.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every API error comes back in the same ErrorResponse shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            PropertyReferenceException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request", req, Map.of());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex, HttpServletRequest req) {
        String message = ex instanceof ForbiddenException ? ex.getMessage() : "Access denied";
        return build(HttpStatus.FORBIDDEN, message, req, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentEdit(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Ticket was modified by someone else, reload and try again", req, Map.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                HttpServletRequest req, Map<String, String> fieldErrors) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, req.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
