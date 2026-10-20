package com.tracker.activity.exception;

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
public class GlobalExceptionHandler {

    // Issue #66: still a 404 with the same detail, but RFC 7807 extension members carry the ranked
    // alternatives, so a typo is actionable instead of a dead end. Extension properties are the
    // sanctioned way to extend ProblemDetail — no new error shape enters the API. Spring dispatches
    // by the most specific exception type, so this wins over handleNotFound below for this subtype.
    @ExceptionHandler(ActivityNameUnresolvedException.class)
    public ProblemDetail handleUnresolvedActivityName(ActivityNameUnresolvedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setProperty("requestedName", ex.getRequestedName());
        problemDetail.setProperty("suggestions", ex.getSuggestions());
        return problemDetail;
    }

    @ExceptionHandler(ActivityNotFoundException.class)
    public ProblemDetail handleNotFound(ActivityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // #79/#80/#88: a caller asking for someone else's userId-keyed resource. See
    // OwnershipViolationException's javadoc for why this is 403, not 404.
    @ExceptionHandler(OwnershipViolationException.class)
    public ProblemDetail handleOwnershipViolation(OwnershipViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InactiveActivityException.class)
    public ProblemDetail handleInactiveActivity(InactiveActivityException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    public ProblemDetail handleTimeout(InvalidTimeRangeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ImplausibleSessionException.class)
    public ProblemDetail handleImplausibleSession(ImplausibleSessionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ReviewStateConflictException.class)
    public ProblemDetail handleReviewStateConflict(ReviewStateConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
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
