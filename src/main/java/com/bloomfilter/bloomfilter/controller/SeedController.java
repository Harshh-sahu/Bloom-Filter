package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.service.SeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/seed")
@RequiredArgsConstructor
@Slf4j
public class SeedController {

    private final SeedService seedService;

    /**
     * POST /api/v1/seed/generate?count=1000000
     *
     * Generates fake usernames for load-testing.  Capped at 100k per request
     * to avoid OOM; call repeatedly to reach 1M+.
     *
     * Example:
     *   for i in $(seq 10); do curl -sX POST "localhost:8080/api/v1/seed/generate?count=100000"; done
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam(defaultValue = "1000") int count) {
        log.info("Seed requested: count={}", count);
        long start = System.currentTimeMillis();
        int created = seedService.generateUsers(count);
        long elapsed = System.currentTimeMillis() - start;
        return ResponseEntity.ok(Map.of(
            "requested", count,
            "created",   created,
            "elapsedMs", elapsed
        ));
    }
}
