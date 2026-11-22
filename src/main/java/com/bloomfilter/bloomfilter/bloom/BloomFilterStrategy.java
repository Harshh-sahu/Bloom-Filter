package com.bloomfilter.bloomfilter.bloom;

import java.util.List;

/**
 * Strategy interface for Bloom filter implementations.
 *
 * Key guarantee: NO FALSE NEGATIVES.
 * - mightContain() returning false means the item is DEFINITELY absent.
 * - mightContain() returning true means the item is POSSIBLY present (may be a false positive).
 *
 * Standard Bloom filters do not support deletion. Workarounds:
 * - Counting Bloom filters (track counts per bit, allow decrements)
 * - Periodic full rebuild from the source of truth (DB)
 * - Cuckoo filters (support deletion with similar space efficiency)
 */
public interface BloomFilterStrategy {

    /**
     * Returns true if the username MIGHT be in the filter (possible false positive).
     * Returns false if the username is DEFINITELY NOT in the filter (no false negatives).
     */
    boolean mightContain(String username);

    /** Adds the username to the filter. */
    void put(String username);

    /** Bulk add during warmup, more efficient than calling put() in a loop. */
    void warmUp(List<String> usernames);

    /** Approximate count of distinct elements added. */
    long approximateElementCount();

    /** Current estimated false positive probability based on actual element count. */
    double currentFpp();

    /** Human-readable identifier for this implementation. */
    String getType();
}
