package com.bloomfilter.bloomfilter.dto;

import java.time.Instant;

public record UsernameCheckResponse(
    String username,
    boolean available,
    String checkedAt
) {
    public static UsernameCheckResponse available(String username) {
        return new UsernameCheckResponse(username, true, Instant.now().toString());
    }

    public static UsernameCheckResponse taken(String username) {
        return new UsernameCheckResponse(username, false, Instant.now().toString());
    }
}
