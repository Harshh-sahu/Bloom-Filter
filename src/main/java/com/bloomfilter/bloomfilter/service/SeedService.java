package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.bloom.BloomFilterStrategy;
import com.bloomfilter.bloomfilter.model.User;
import com.bloomfilter.bloomfilter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates fake usernames to seed the database for load testing.
 *
 * The whole generation runs in a single transaction (acceptable for a seed endpoint).
 * Batches of 500 bound the JDBC batch insert size; all share the same connection/TX.
 * For very large counts (> 500k), consider calling this endpoint multiple times
 * since the persistence context holds all entities until commit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeedService {

    private static final List<String> PREFIXES = List.of(
        "user", "dev", "pro", "cool", "super", "mega", "ultra", "tech",
        "web", "app", "sky", "star", "nova", "code", "hack"
    );
    private static final int    BATCH_SIZE = 500;

    private final UserRepository      userRepository;
    private final BloomFilterStrategy bloomFilter;

    @Transactional
    public int generateUsers(int count) {
        int remaining = Math.min(count, 100_000);
        int created   = 0;
        Random rnd    = new Random();

        while (remaining > 0) {
            int batchCount = Math.min(remaining, BATCH_SIZE);
            List<User> batch = new ArrayList<>(batchCount);

            for (int i = 0; i < batchCount; i++) {
                String prefix   = PREFIXES.get(rnd.nextInt(PREFIXES.size()));
                String suffix   = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String username = prefix + "_" + suffix;
                batch.add(User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .build());
            }

            List<User> saved = userRepository.saveAll(batch);
            saved.forEach(u -> bloomFilter.put(u.getUsername()));
            created   += saved.size();
            remaining -= saved.size();

            if (created % 5000 == 0) {
                log.info("Seeded {} users...", created);
            }
        }

        log.info("Seed complete: {} users generated", created);
        return created;
    }
}
