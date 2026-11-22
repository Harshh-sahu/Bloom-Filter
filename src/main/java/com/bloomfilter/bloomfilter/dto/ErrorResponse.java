package com.bloomfilter.bloomfilter.dto;

import java.time.Instant;

public record ErrorResponse(
    String code,
    String message,
    String timestamp
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now().toString());
    }
}
