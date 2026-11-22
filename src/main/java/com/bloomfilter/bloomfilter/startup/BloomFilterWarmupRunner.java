package com.bloomfilter.bloomfilter.startup;

import com.bloomfilter.bloomfilter.bloom.BloomFilterStrategy;
import com.bloomfilter.bloomfilter.config.BloomFilterProperties;
import com.bloomfilter.bloomfilter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Warms up the Bloom filter at startup by streaming all existing usernames
 * from PostgreSQL in batches of {@code bloom-filter.batch-size}.
 *
 * Uses a server-side cursor (Hibernate fetchSize hint) so that even 10M+ rows
 * never load fully into heap at once.  The streaming query runs in a read-only
 * transaction; the try-with-resources ensures the cursor is always closed.
 *
 * Persistence across restarts:
 *   This rebuild-from-DB approach is the simplest option.  Alternatives:
 *   a) Serialise the BloomFilter (Guava supports this) to disk / S3 on shutdown
 *      and restore on startup — faster boot, risk of stale snapshot.
 *   b) Use a centralised Redis Bloom filter (Redisson RBloomFilter) so each
 *      instance shares state without a full rebuild.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BloomFilterWarmupRunner implements ApplicationRunner {

    private final BloomFilterStrategy   bloomFilter;
    private final UserRepository        userRepository;
    private final BloomFilterProperties properties;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        if (!properties.isWarmupOnStartup()) {
            log.info("Bloom filter warmup disabled (bloom-filter.warmup-on-startup=false)");
            return;
        }

        log.info("Starting Bloom filter warmup (batch-size={})...", properties.getBatchSize());
        long startMs = System.currentTimeMillis();
        AtomicLong total = new AtomicLong(0);

        try (Stream<String> stream = userRepository.streamAllUsernames()) {
            List<String> batch = new ArrayList<>(properties.getBatchSize());

            stream.forEach(username -> {
                batch.add(username);
                if (batch.size() >= properties.getBatchSize()) {
                    bloomFilter.warmUp(List.copyOf(batch));
                    total.addAndGet(batch.size());
                    batch.clear();
                    log.info("  … loaded {} usernames into Bloom filter", total.get());
                }
            });

            if (!batch.isEmpty()) {
                bloomFilter.warmUp(batch);
                total.addAndGet(batch.size());
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        log.info("Bloom filter warmup complete: {} usernames loaded in {} ms "
            + "(approx. elements={}, current FPP={})",
            total.get(), elapsed,
            bloomFilter.approximateElementCount(),
            String.format("%.6f", bloomFilter.currentFpp()));
    }
}
