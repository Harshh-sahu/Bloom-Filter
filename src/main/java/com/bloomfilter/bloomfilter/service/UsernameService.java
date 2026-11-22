package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.dto.UsernameCheckResponse;

public interface UsernameService {

    /**
     * Check if a username is available.
     * Fast path: Bloom filter negative → return immediately, no DB call.
     * Slow path: Bloom filter positive → check Redis → check PostgreSQL.
     */
    UsernameCheckResponse checkAvailability(String rawUsername);

    /**
     * Normalize a raw username: trim, lowercase, validate pattern.
     * Throws ValidationException if the username is invalid.
     */
    String normalize(String rawUsername);
}
