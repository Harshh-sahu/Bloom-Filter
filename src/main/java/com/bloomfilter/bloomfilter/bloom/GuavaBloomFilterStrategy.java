package com.bloomfilter.bloomfilter.bloom;

import com.bloomfilter.bloomfilter.config.BloomFilterProperties;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Bloom filter backed by Guava's BloomFilter<String>.
 *
 * Guava computes m and k for you given n and p:
 *   m = ceil( -n * ln(p) / (ln 2)^2 )
 *   k = round( (m/n) * ln 2 )
 *
 * Guava uses an optimised MurmurHash3 + double-hashing internally and stores
 * bits in a long[].  It is thread-safe (uses AtomicLongArray).
 *
 * Memory at defaults (n=10M, p=0.1%):
 *   m ≈ 143,775,479 bits ≈ 17 MB
 *   k = 10 hash functions
 */
@Slf4j
public class GuavaBloomFilterStrategy implements BloomFilterStrategy {

    private final BloomFilter<String> bloomFilter;

    public GuavaBloomFilterStrategy(BloomFilterProperties props) {
        this.bloomFilter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            props.getExpectedInsertions(),
            props.getFalsePositiveProbability()
        );
        logStats(props);
    }

    private void logStats(BloomFilterProperties props) {
        long n = props.getExpectedInsertions();
        double p = props.getFalsePositiveProbability();
        long m = (long) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        int k = (int) Math.round(((double) m / n) * Math.log(2));

        log.info("=== Guava Bloom Filter Configuration ===");
        log.info("  Expected insertions  (n) = {}", n);
        log.info("  False-positive prob  (p) = {}", p);
        log.info("  Bit-array size       (m) = {} bits  ({} MB)", m, m / 8 / 1024 / 1024);
        log.info("  Hash functions       (k) = {}", k);
        log.info("Formulas: m = ceil(-n*ln(p)/(ln2)^2)  |  k = round((m/n)*ln2)");
        log.info("No false negatives: bits are set on insertion and never cleared;");
        log.info("  if ANY of the k bits is 0, the key was definitely never inserted.");
    }

    @Override
    public boolean mightContain(String username) {
        return bloomFilter.mightContain(username);
    }

    @Override
    public void put(String username) {
        bloomFilter.put(username);
    }

    @Override
    public void warmUp(List<String> usernames) {
        usernames.forEach(bloomFilter::put);
    }

    @Override
    public long approximateElementCount() {
        return bloomFilter.approximateElementCount();
    }

    @Override
    public double currentFpp() {
        return bloomFilter.expectedFpp();
    }

    @Override
    public String getType() {
        return "guava";
    }
}
