package com.pulseclinic.common.web;

import com.pulseclinic.appointment.AppointmentSlotUnavailableException;
import com.pulseclinic.appointment.InvalidAppointmentRequestException;
import com.pulseclinic.auth.AuthException;
import com.pulseclinic.appointment.InvalidAppointmentStateException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppointmentSlotUnavailableException.class)
    public ResponseEntity<ApiError> handleSlotUnavailable(
            AppointmentSlotUnavailableException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "APPOINTMENT_SLOT_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidAppointmentRequestException.class)
    public ResponseEntity<ApiError> handleInvalidAppointmentRequest(
            InvalidAppointmentRequestException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_APPOINTMENT_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiError> handleAuth(AuthException ex, HttpServletRequest request) {
        HttpStatus status = "INVALID_CREDENTIALS".equals(ex.code()) || "UNAUTHENTICATED".equals(ex.code())
                ? HttpStatus.UNAUTHORIZED : HttpStatus.CONFLICT;
        return error(status, ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(InvalidAppointmentStateException.class)
    public ResponseEntity<ApiError> handleInvalidAppointmentState(InvalidAppointmentStateException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_APPOINTMENT_STATE", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, Instant.now(), request.getRequestURI()));
    }
}
