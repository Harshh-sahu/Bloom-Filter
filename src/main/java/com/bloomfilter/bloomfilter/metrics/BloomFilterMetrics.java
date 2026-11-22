package com.bloomfilter.bloomfilter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer-based metrics for the Bloom filter check pipeline.
 * All metrics are exported via /actuator/prometheus.
 *
 * Metric names (Prometheus uses underscores):
 *   bloom_filter_negative_total        – Bloom filter said "definitely absent" (fast path)
 *   bloom_filter_positive_total        – Bloom filter said "might be present"
 *   bloom_filter_false_positive_total  – Bloom said maybe, DB said no (measurable FPP)
 *   bloom_filter_cache_hit_total       – Redis answered before DB was needed
 *   bloom_filter_cache_miss_total      – Redis miss, DB query required
 *   bloom_filter_check_seconds         – Full check request latency histogram
 */
@Component
@RequiredArgsConstructor
public class BloomFilterMetrics {

    private final MeterRegistry meterRegistry;

    private Counter bloomNegativeCounter;
    private Counter bloomPositiveCounter;
    private Counter falsePositiveCounter;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Timer   checkTimer;

    @PostConstruct
    public void init() {
        bloomNegativeCounter = Counter.builder("bloom_filter.negative")
            .description("Bloom filter definite-negative count (no DB call)")
            .register(meterRegistry);

        bloomPositiveCounter = Counter.builder("bloom_filter.positive")
            .description("Bloom filter potential-positive count (triggered DB/cache check)")
            .register(meterRegistry);

        falsePositiveCounter = Counter.builder("bloom_filter.false_positive")
            .description("Bloom said maybe, DB confirmed absent (actual FPP events)")
            .register(meterRegistry);

        cacheHitCounter = Counter.builder("bloom_filter.cache_hit")
            .description("Redis cache hits that avoided a DB query")
            .register(meterRegistry);

        cacheMissCounter = Counter.builder("bloom_filter.cache_miss")
            .description("Redis cache misses that required a DB query")
            .register(meterRegistry);

        checkTimer = Timer.builder("bloom_filter.check")
            .description("End-to-end username check latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    public void recordBloomNegative()  { bloomNegativeCounter.increment(); }
    public void recordBloomPositive()  { bloomPositiveCounter.increment(); }
    public void recordFalsePositive()  { falsePositiveCounter.increment(); }
    public void recordCacheHit()       { cacheHitCounter.increment(); }
    public void recordCacheMiss()      { cacheMissCounter.increment(); }

    public void recordCheckLatency(long nanoseconds) {
        checkTimer.record(nanoseconds, TimeUnit.NANOSECONDS);
    }

    /** Cache hit ratio: hits / (hits + misses). */
    public double getCacheHitRatio() {
        double hits = cacheHitCounter.count();
        double total = hits + cacheMissCounter.count();
        return total > 0 ? hits / total : 0.0;
    }

    /** Observed false-positive rate: fp / (positives). */
    public double getObservedFpp() {
        double fp  = falsePositiveCounter.count();
        double pos = bloomPositiveCounter.count();
        return pos > 0 ? fp / pos : 0.0;
    }
}
