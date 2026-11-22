package com.bloomfilter.bloomfilter.config;

import com.bloomfilter.bloomfilter.bloom.BloomFilterStrategy;
import com.bloomfilter.bloomfilter.bloom.CustomBloomFilterStrategy;
import com.bloomfilter.bloomfilter.bloom.GuavaBloomFilterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BloomFilterProperties.class)
@Slf4j
public class BloomFilterConfig {

    /**
     * Factory that creates the correct BloomFilterStrategy based on application.yml.
     * Adding a new implementation is a one-line change here plus a new class.
     *
     * Multi-instance note: both strategies are in-process (per JVM).
     * For a shared filter across instances, swap this bean for a
     * RedissonBloomFilterStrategy wrapping Redisson's RBloomFilter.
     */
    @Bean
    public BloomFilterStrategy bloomFilterStrategy(BloomFilterProperties properties) {
        String strategy = properties.getStrategy();
        log.info("Creating Bloom filter with strategy: {}", strategy);

        return switch (strategy.toLowerCase()) {
            case "custom" -> {
                log.info("Using hand-written BitSet + MurmurHash3 Bloom filter");
                yield new CustomBloomFilterStrategy(properties);
            }
            default -> {
                log.info("Using Guava BloomFilter (thread-safe, AtomicLongArray-backed)");
                yield new GuavaBloomFilterStrategy(properties);
            }
        };
    }
}
