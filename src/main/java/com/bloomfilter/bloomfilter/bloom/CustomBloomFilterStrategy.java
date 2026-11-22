package com.bloomfilter.bloomfilter.bloom;

import com.bloomfilter.bloomfilter.config.BloomFilterProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-written Bloom filter using BitSet + MurmurHash3-inspired 64-bit hashing.
 *
 * ==========================================================================
 * HOW IT WORKS
 * ==========================================================================
 *
 * DATA STRUCTURE
 * --------------
 * A bit array of size m (a Java BitSet wrapping long[]). All bits start at 0.
 *
 * INSERTION  put(x)
 * ----------------
 * Compute k independent positions in [0, m) and set those bits to 1.
 * We derive k positions via double-hashing:
 *
 *   index_i(x) = | h1(x) + i * h2(x) | mod m    for i in [0, k)
 *
 * where h1, h2 are two independent 64-bit MurmurHash3 invocations with
 * different seeds.  This avoids storing k independent hash functions while
 * maintaining near-independent bit positions.
 *
 * LOOKUP  mightContain(x)
 * -----------------------
 * Compute the same k bit positions. If ALL bits are 1 → "maybe present".
 * If ANY bit is 0 → "definitely absent" (no false negatives possible).
 *
 * FALSE POSITIVES
 * ---------------
 * As the filter fills up, bit collisions create false positives: bits set by
 * OTHER keys happen to match the k positions of a query key. The DB check
 * catches these.
 *
 * FALSE NEGATIVES: IMPOSSIBLE
 * ---------------------------
 * Bits are only ever SET (1), never cleared. So once a key is inserted, its
 * k bit positions remain 1 forever → lookup can never return false for it.
 *
 * SIZING FORMULAS
 * ---------------
 *   m = ceil( -n · ln(p) / (ln 2)^2 )
 *   k = round( (m/n) · ln 2 )
 *
 * where n = expected elements, p = desired false-positive probability.
 *
 * CURRENT FPP (after t insertions)
 * ---------------------------------
 *   FPP(t) = (1 - e^(-k·t/m))^k
 *
 * DELETION
 * --------
 * Not supported by standard Bloom filters. Deleting a key would require
 * clearing its k bits, but those bits may be shared with other keys.
 * Alternatives:
 *   1. Counting Bloom filter: each "bit" is a counter; decrement on delete.
 *      Cost: 4-8× more memory.
 *   2. Cuckoo filter: supports deletion, often more space-efficient.
 *   3. Periodic rebuild: schedule a full DB scan to reconstruct the filter.
 *
 * MULTI-INSTANCE DEPLOYMENT
 * -------------------------
 * This in-process filter is inconsistent across JVM instances.
 * Production options:
 *   a) Redis Bloom (RedisBloom module): centralised, O(1) per check.
 *      Use Redisson RBloomFilter or Jedis with BF.ADD / BF.EXISTS.
 *   b) Redisson RBloomFilter: Java client, backed by Redis bit-arrays.
 *   c) Shared rebuild: each instance rebuilds from DB on startup + listens
 *      for insert events via Redis Pub/Sub or Kafka to stay in sync.
 *   Trade-off: Redis adds ~0.5-1 ms network latency vs. sub-100 µs in-process.
 * ==========================================================================
 */
@Slf4j
public class CustomBloomFilterStrategy implements BloomFilterStrategy {

    private final BitSet bitSet;
    private final int m;        // bit-array size
    private final int k;        // number of hash functions
    private final AtomicLong count = new AtomicLong(0);

    // MurmurHash3 mix constants
    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;

    public CustomBloomFilterStrategy(BloomFilterProperties props) {
        long n = props.getExpectedInsertions();
        double p = props.getFalsePositiveProbability();

        // m = ceil( -n * ln(p) / (ln(2))^2 )
        long mLong = (long) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        // Clamp to Integer.MAX_VALUE; for n>~2.5B you need a different backing store.
        this.m = (int) Math.min(mLong, Integer.MAX_VALUE);

        // k = round( (m/n) * ln(2) )
        this.k = Math.max(1, (int) Math.round(((double) m / n) * Math.log(2)));

        this.bitSet = new BitSet(m);

        log.info("=== Custom Bloom Filter Configuration ===");
        log.info("  Expected insertions  (n) = {}", n);
        log.info("  False-positive prob  (p) = {}", p);
        log.info("  Bit-array size       (m) = {} bits  ({} MB)", m, (long) m / 8 / 1024 / 1024);
        log.info("  Hash functions       (k) = {}", k);
        log.info("  Strategy             = double-hashing with MurmurHash3");
    }

    @Override
    public boolean mightContain(String username) {
        long[] h = getHashes(username);
        for (int i = 0; i < k; i++) {
            if (!bitSet.get(getBitIndex(h, i))) {
                return false; // definitely absent
            }
        }
        return true; // might be present
    }

    @Override
    public void put(String username) {
        long[] h = getHashes(username);
        for (int i = 0; i < k; i++) {
            bitSet.set(getBitIndex(h, i));
        }
        count.incrementAndGet();
    }

    @Override
    public void warmUp(List<String> usernames) {
        usernames.forEach(this::put);
    }

    // Double-hashing: index_i = |h1 + i*h2| mod m
    private int getBitIndex(long[] h, int i) {
        long combined = h[0] + (long) i * h[1];
        int index = (int) (combined % m);
        return index < 0 ? index + m : index;
    }

    // Two independent 64-bit hashes via MurmurHash3 finaliser with different seeds
    private long[] getHashes(String key) {
        return new long[]{murmurHash64(key, 0x9747b28cL), murmurHash64(key, 0x5f4a0bc5L)};
    }

    /**
     * MurmurHash3-style 64-bit hash.
     * Processes 8 bytes at a time, then handles the tail, then applies the
     * finalisation mix (fmix64) for avalanche.
     */
    private long murmurHash64(String key, long seed) {
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        int len = data.length;
        long h = seed ^ (len * C1);

        int i = 0;
        while (i + 8 <= len) {
            long word = readLe64(data, i);
            word *= C1;
            word = Long.rotateLeft(word, 31);
            word *= C2;
            h ^= word;
            h = Long.rotateLeft(h, 27);
            h = h * 5 + 0x52dce729L;
            i += 8;
        }

        // tail bytes
        long tail = 0L;
        for (int j = len - 1; j >= i; j--) {
            tail = (tail << 8) | (data[j] & 0xFFL);
        }
        if (len > i) {
            tail *= C1;
            tail = Long.rotateLeft(tail, 31);
            tail *= C2;
            h ^= tail;
        }

        h ^= len;
        return fmix64(h);
    }

    private long readLe64(byte[] b, int off) {
        return  (b[off]   & 0xFFL)        | ((b[off+1] & 0xFFL) << 8)
              | ((b[off+2] & 0xFFL) << 16) | ((b[off+3] & 0xFFL) << 24)
              | ((b[off+4] & 0xFFL) << 32) | ((b[off+5] & 0xFFL) << 40)
              | ((b[off+6] & 0xFFL) << 48) | ((b[off+7] & 0xFFL) << 56);
    }

    /** fmix64 finaliser for good avalanche effect. */
    private long fmix64(long h) {
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }

    @Override
    public long approximateElementCount() {
        return count.get();
    }

    @Override
    public double currentFpp() {
        // FPP(t) = (1 - e^(-k*t/m))^k
        double t = count.get();
        return Math.pow(1.0 - Math.exp(-(double) k * t / m), k);
    }

    @Override
    public String getType() {
        return "custom";
    }
}
