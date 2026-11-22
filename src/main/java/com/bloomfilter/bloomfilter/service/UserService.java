package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.dto.UserCreateRequest;
import com.bloomfilter.bloomfilter.dto.UserCreateResponse;

public interface UserService {

    /**
     * Register a new user. Inserts into PostgreSQL, then updates the Bloom filter and Redis.
     * Throws UsernameAlreadyTakenException on 409 conflict (DB unique-constraint violation).
     */
    UserCreateResponse createUser(UserCreateRequest request);
}
