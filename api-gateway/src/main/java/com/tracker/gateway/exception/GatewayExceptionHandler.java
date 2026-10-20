package com.tracker.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GatewayExceptionHandler {

    // A @Valid failure used to fall through to Spring's default body (and, before the validation
    // starter was added, never fired at all). 400 with an "errors" extension member mapping each
    // offending field to its message, so a form can mark the exact field. "detail" keeps the
    // human-readable messages joined, matching what activity-service and gamification-service
    // already return, so a client that only shows "detail" still gets something useful.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.merge(fieldError.getField(), messageOf(fieldError.getDefaultMessage()),
                    (first, second) -> first + "; " + second);
        }
        // Class-level constraints have no field; key them by object name rather than drop them.
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            errors.merge(globalError.getObjectName(), messageOf(globalError.getDefaultMessage()),
                    (first, second) -> first + "; " + second);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                String.join("; ", errors.values()));
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String messageOf(String defaultMessage) {
        return defaultMessage == null ? "invalid" : defaultMessage;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // Duplicate registration used to surface as a bare 500 from the unique-email constraint.
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
