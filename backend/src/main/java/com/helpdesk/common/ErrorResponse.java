package com.helpdesk.common;

import java.time.Instant;
import java.util.Map;

/** Every API error uses this shape, so tests can assert on it with one JSON schema. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
