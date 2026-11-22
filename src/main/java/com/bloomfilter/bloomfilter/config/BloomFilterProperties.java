package com.bloomfilter.bloomfilter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bloom-filter")
public class BloomFilterProperties {

    /** Expected number of distinct insertions (n). Affects bit-array size. */
    private long expectedInsertions = 10_000_000L;

    /**
     * Desired false-positive probability (p), e.g. 0.001 = 0.1%.
     * Lower FPP → more bits → more memory, fewer DB round-trips.
     * m = ceil( -n * ln(p) / (ln(2))^2 )
     */
    private double falsePositiveProbability = 0.001;

    /** "guava" uses Guava BloomFilter; "custom" uses hand-written BitSet+MurmurHash3. */
    private String strategy = "guava";

    /** Batch size for streaming usernames from DB during warmup. */
    private int batchSize = 10_000;

    /** Set to false to skip warmup (useful for integration tests). */
    private boolean warmupOnStartup = true;
}
