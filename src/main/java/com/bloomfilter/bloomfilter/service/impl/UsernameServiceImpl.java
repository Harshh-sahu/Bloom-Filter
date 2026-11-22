package com.bloomfilter.bloomfilter.service.impl;

import com.bloomfilter.bloomfilter.bloom.BloomFilterStrategy;
import com.bloomfilter.bloomfilter.dto.UsernameCheckResponse;
import com.bloomfilter.bloomfilter.exception.ValidationException;
import com.bloomfilter.bloomfilter.metrics.BloomFilterMetrics;
import com.bloomfilter.bloomfilter.repository.UserRepository;
import com.bloomfilter.bloomfilter.service.UsernameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Core check pipeline:
 *
 *   normalize → Bloom filter
 *                  │ definitely absent (no false negatives)
 *                  │───────────────────────────────► available=true (NO DB CALL)
 *                  │ might be present
 *                  ▼
 *              Redis cache
 *                  │ hit
 *                  │───────────────────────────────► available=false
 *                  │ miss
 *                  ▼
 *              PostgreSQL
 *                  │ exists
 *                  │───────────────────────────────► available=false (+ cache the result)
 *                  │ absent (Bloom false positive)
 *                  └───────────────────────────────► available=true  (+ record FP metric)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsernameServiceImpl implements UsernameService {

    private static final Pattern USERNAME_PATTERN =
        Pattern.compile("^[a-z0-9._]{3,50}$");

    private static final String REDIS_PREFIX = "username:taken:";
    private static final Duration CACHE_TTL  = Duration.ofHours(24);

    private final BloomFilterStrategy bloomFilter;
    private final StringRedisTemplate  redisTemplate;
    private final UserRepository       userRepository;
    private final BloomFilterMetrics   metrics;

    @Override
    public UsernameCheckResponse checkAvailability(String rawUsername) {
        long startNs = System.nanoTime();
        try {
            String username = normalize(rawUsername);
            return doCheck(username);
        } finally {
            metrics.recordCheckLatency(System.nanoTime() - startNs);
        }
    }

    private UsernameCheckResponse doCheck(String username) {
        // ── Step 1: Bloom filter ──────────────────────────────────────────
        if (!bloomFilter.mightContain(username)) {
            // Definite negative — skip Redis AND PostgreSQL entirely.
            // This is the hot path that makes single-digit-ms latency possible.
            metrics.recordBloomNegative();
            log.debug("Bloom negative → {} is available (no DB call)", username);
            return UsernameCheckResponse.available(username);
        }

        metrics.recordBloomPositive();

        // ── Step 2: Redis cache ───────────────────────────────────────────
        Boolean inCache = redisTemplate.hasKey(REDIS_PREFIX + username);
        if (Boolean.TRUE.equals(inCache)) {
            metrics.recordCacheHit();
            log.debug("Redis cache hit → {} is taken", username);
            return UsernameCheckResponse.taken(username);
        }
        metrics.recordCacheMiss();

        // ── Step 3: PostgreSQL source of truth ────────────────────────────
        boolean existsInDb = userRepository.existsByUsername(username);
        if (existsInDb) {
            // Warm up the cache so the next check for this username is fast.
            redisTemplate.opsForValue().set(REDIS_PREFIX + username, "1", CACHE_TTL);
            log.debug("DB hit → {} is taken (cached for {})", username, CACHE_TTL);
            return UsernameCheckResponse.taken(username);
        }

        // Bloom filter was a false positive (the bits were set by other keys).
        metrics.recordFalsePositive();
        log.debug("Bloom false positive → {} is actually available", username);
        return UsernameCheckResponse.available(username);
    }

    @Override
    public String normalize(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            throw new ValidationException("Username cannot be blank");
        }
        String normalized = rawUsername.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(
                "Username must be 3–50 characters, lowercase letters/digits/dots/underscores only");
        }
        return normalized;
    }
}
