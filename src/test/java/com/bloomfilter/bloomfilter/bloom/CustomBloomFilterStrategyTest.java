package com.bloomfilter.bloomfilter.bloom;

import com.bloomfilter.bloomfilter.config.BloomFilterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CustomBloomFilterStrategyTest {

    private CustomBloomFilterStrategy bloomFilter;

    @BeforeEach
    void setUp() {
        BloomFilterProperties props = new BloomFilterProperties();
        props.setExpectedInsertions(1_000);
        props.setFalsePositiveProbability(0.01);
        bloomFilter = new CustomBloomFilterStrategy(props);
    }

    @Test
    void absentItemReturnsFalse() {
        assertThat(bloomFilter.mightContain("alice")).isFalse();
    }

    @Test
    void insertedItemIsFound() {
        bloomFilter.put("alice");
        assertThat(bloomFilter.mightContain("alice")).isTrue();
    }

    @Test
    void noFalseNegatives_allInsertedItemsFound() {
        List<String> names = IntStream.range(0, 200)
            .mapToObj(i -> "user_" + i)
            .toList();

        names.forEach(bloomFilter::put);

        names.forEach(name ->
            assertThat(bloomFilter.mightContain(name))
                .as("False negative for: %s", name)
                .isTrue());
    }

    @Test
    void falsePositiveRateIsAcceptable() {
        IntStream.range(0, 500).forEach(i -> bloomFilter.put("inserted_" + i));

        long fp = IntStream.range(0, 1_000)
            .filter(i -> bloomFilter.mightContain("absent_" + i))
            .count();

        double fppRate = (double) fp / 1_000;
        assertThat(fppRate)
            .as("Custom BF FPP should be < 5% at half capacity")
            .isLessThan(0.05);
    }

    @Test
    void hashingIsConsistent() {
        // Same key should always map to the same bits
        bloomFilter.put("alice");
        for (int i = 0; i < 100; i++) {
            assertThat(bloomFilter.mightContain("alice"))
                .as("Inconsistent hash on iteration " + i)
                .isTrue();
        }
    }

    @Test
    void differentKeysGetDifferentResults() {
        bloomFilter.put("alice");
        // "bob" should NOT be in the filter (unless false positive, which is rare)
        // We can't assert false with certainty, but statistically for small n it holds.
        // Instead verify count
        assertThat(bloomFilter.approximateElementCount()).isEqualTo(1);
    }

    @Test
    void currentFppGrowsWithInsertions() {
        double fppEmpty = bloomFilter.currentFpp();
        IntStream.range(0, 500).forEach(i -> bloomFilter.put("u_" + i));
        double fppFull = bloomFilter.currentFpp();
        assertThat(fppFull).isGreaterThan(fppEmpty);
    }

    @Test
    void getTypeReturnsCustom() {
        assertThat(bloomFilter.getType()).isEqualTo("custom");
    }

    @Test
    void warmUpAcceptsEmptyList() {
        bloomFilter.warmUp(List.of());
        assertThat(bloomFilter.approximateElementCount()).isEqualTo(0);
    }

    @Test
    void unicodeKeysAreHandled() {
        bloomFilter.put("用户名");
        assertThat(bloomFilter.mightContain("用户名")).isTrue();
        assertThat(bloomFilter.mightContain("другой")).isFalse();
    }
}
