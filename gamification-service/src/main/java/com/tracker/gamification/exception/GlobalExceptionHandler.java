package com.tracker.gamification.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // #76/#88: a caller asking for someone else's userId-keyed resource. See
    // OwnershipViolationException's javadoc for why this is 403, not 404.
    @ExceptionHandler(OwnershipViolationException.class)
    public ProblemDetail handleOwnershipViolation(OwnershipViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidThresholdCurveException.class)
    public ProblemDetail handleInvalidThresholdCurve(InvalidThresholdCurveException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleInvalidRequestBody(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
    }

    // @Valid on a request body fails with MethodArgumentNotValidException rather than a custom
    // exception. 400 with an "errors" extension member mapping each offending field to its message
    // so a form can mark the exact field. "detail" stays the joined human-readable messages it has
    // always been, so a client that only shows "detail" keeps working.
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
}
