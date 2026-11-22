package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.dto.UserCreateRequest;
import com.bloomfilter.bloomfilter.dto.UserCreateResponse;
import com.bloomfilter.bloomfilter.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/v1/users
     * Body: { "username": "alice", "email": "alice@example.com" }
     *
     * Returns 201 on success, 409 if username is already taken.
     * Race condition: two concurrent requests for the same username both pass
     * the Bloom filter check; the DB unique constraint makes exactly one succeed
     * and the other gets a 409.
     */
    @PostMapping
    public ResponseEntity<UserCreateResponse> register(@RequestBody @Valid UserCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(req));
    }
}
