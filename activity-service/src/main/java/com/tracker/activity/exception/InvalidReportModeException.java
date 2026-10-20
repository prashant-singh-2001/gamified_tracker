package com.tracker.activity.exception;

/** {@code ?mode=} was neither {@code rolling} nor {@code calendar}. Mapped to 400. */
public class InvalidReportModeException extends RuntimeException {
    public InvalidReportModeException(String raw) {
        super("mode must be one of: rolling, calendar (got '" + raw + "')");
    }
}
