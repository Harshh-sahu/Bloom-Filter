package com.bloomfilter.bloomfilter.bloom;

import com.bloomfilter.bloomfilter.config.BloomFilterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GuavaBloomFilterStrategyTest {

    private GuavaBloomFilterStrategy bloomFilter;

    @BeforeEach
    void setUp() {
        BloomFilterProperties props = new BloomFilterProperties();
        props.setExpectedInsertions(1_000);
        props.setFalsePositiveProbability(0.01);
        bloomFilter = new GuavaBloomFilterStrategy(props);
    }

    @Test
    void absentItemReturnsFalse() {
        assertThat(bloomFilter.mightContain("alice")).isFalse();
        assertThat(bloomFilter.mightContain("bob")).isFalse();
    }

    @Test
    void insertedItemIsFound() {
        bloomFilter.put("alice");
        assertThat(bloomFilter.mightContain("alice")).isTrue();
    }

    @Test
    void noFalseNegatives() {
        List<String> names = IntStream.range(0, 200)
            .mapToObj(i -> "user_" + i)
            .toList();

        names.forEach(bloomFilter::put);

        // Every inserted item MUST be found — no false negatives allowed
        names.forEach(name ->
            assertThat(bloomFilter.mightContain(name))
                .as("False negative for: %s", name)
                .isTrue());
    }

    @Test
    void falsePositiveRateIsWithinTarget() {
        // Insert 500 items (half the capacity)
        IntStream.range(0, 500).forEach(i -> bloomFilter.put("inserted_" + i));

        // Check 1000 non-inserted items; FPP should stay near 1%
        long falsePositives = IntStream.range(0, 1_000)
            .filter(i -> bloomFilter.mightContain("absent_" + i))
            .count();

        double fppRate = (double) falsePositives / 1_000;
        assertThat(fppRate).as("FPP rate should be < 5%").isLessThan(0.05);
    }

    @Test
    void warmUpAddsMultipleItems() {
        bloomFilter.warmUp(List.of("alice", "bob", "charlie"));
        assertThat(bloomFilter.mightContain("alice")).isTrue();
        assertThat(bloomFilter.mightContain("bob")).isTrue();
        assertThat(bloomFilter.mightContain("charlie")).isTrue();
        assertThat(bloomFilter.mightContain("diana")).isFalse();
    }

    @Test
    void approximateElementCountIncreases() {
        long before = bloomFilter.approximateElementCount();
        bloomFilter.put("alice");
        bloomFilter.put("bob");
        assertThat(bloomFilter.approximateElementCount()).isGreaterThan(before);
    }

    @Test
    void getTypeReturnsGuava() {
        assertThat(bloomFilter.getType()).isEqualTo("guava");
    }
}
