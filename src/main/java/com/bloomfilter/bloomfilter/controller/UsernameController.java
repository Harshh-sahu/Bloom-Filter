package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.dto.UsernameCheckResponse;
import com.bloomfilter.bloomfilter.service.UsernameService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usernames")
@RequiredArgsConstructor
@Validated
public class UsernameController {

    private final UsernameService usernameService;

    /**
     * GET /api/v1/usernames/check?username=abc
     *
     * Fast path  (~0.1 ms): Bloom filter says "definitely absent" → no DB call.
     * Slow path  (~2–5 ms): Bloom filter says "maybe" → Redis → PostgreSQL.
     *
     * Response:
     *   { "username": "alice", "available": true,  "checkedAt": "2024-…" }
     *   { "username": "alice", "available": false, "checkedAt": "2024-…" }
     */
    @GetMapping("/check")
    public ResponseEntity<UsernameCheckResponse> check(
            @RequestParam @NotBlank(message = "username must not be blank") String username) {
        return ResponseEntity.ok(usernameService.checkAvailability(username));
    }
}
