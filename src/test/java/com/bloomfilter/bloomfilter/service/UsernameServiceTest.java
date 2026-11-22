package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.bloom.BloomFilterStrategy;
import com.bloomfilter.bloomfilter.dto.UsernameCheckResponse;
import com.bloomfilter.bloomfilter.exception.ValidationException;
import com.bloomfilter.bloomfilter.metrics.BloomFilterMetrics;
import com.bloomfilter.bloomfilter.repository.UserRepository;
import com.bloomfilter.bloomfilter.service.impl.UsernameServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsernameServiceTest {

    @Mock BloomFilterStrategy  bloomFilter;
    @Mock StringRedisTemplate  redisTemplate;
    @Mock UserRepository       userRepository;
    @Mock BloomFilterMetrics   metrics;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks UsernameServiceImpl usernameService;

    // ── Fast path: Bloom says definitely absent ───────────────────────────

    @Test
    void bloomNegative_returnsAvailableWithoutDbCall() {
        when(bloomFilter.mightContain("alice")).thenReturn(false);

        UsernameCheckResponse resp = usernameService.checkAvailability("alice");

        assertThat(resp.available()).isTrue();
        assertThat(resp.username()).isEqualTo("alice");
        verify(userRepository, never()).existsByUsername(any());
        verify(redisTemplate, never()).hasKey(any());
        verify(metrics).recordBloomNegative();
    }

    // ── Slow path: Bloom positive + Redis hit ─────────────────────────────

    @Test
    void bloomPositive_cacheHit_returnsTaken() {
        when(bloomFilter.mightContain("alice")).thenReturn(true);
        when(redisTemplate.hasKey("username:taken:alice")).thenReturn(true);

        UsernameCheckResponse resp = usernameService.checkAvailability("alice");

        assertThat(resp.available()).isFalse();
        verify(userRepository, never()).existsByUsername(any());
        verify(metrics).recordBloomPositive();
        verify(metrics).recordCacheHit();
    }

    // ── Slow path: Bloom positive, Redis miss, DB hit ─────────────────────

    @Test
    void bloomPositive_cacheMiss_dbHit_returnsTaken() {
        when(bloomFilter.mightContain("alice")).thenReturn(true);
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        UsernameCheckResponse resp = usernameService.checkAvailability("alice");

        assertThat(resp.available()).isFalse();
        verify(userRepository).existsByUsername("alice");
        verify(valueOps).set(eq("username:taken:alice"), eq("1"), any());
        verify(metrics).recordCacheMiss();
    }

    // ── False positive: Bloom positive, Redis miss, DB miss ───────────────

    @Test
    void bloomPositive_cacheMiss_dbMiss_recordsFalsePositive() {
        when(bloomFilter.mightContain("alice")).thenReturn(true);
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);

        UsernameCheckResponse resp = usernameService.checkAvailability("alice");

        assertThat(resp.available()).isTrue();
        verify(metrics).recordFalsePositive();
    }

    // ── Normalisation ─────────────────────────────────────────────────────

    @Test
    void normalize_trimsAndLowercases() {
        when(bloomFilter.mightContain("alice")).thenReturn(false);

        UsernameCheckResponse resp = usernameService.checkAvailability("  ALICE  ");

        assertThat(resp.username()).isEqualTo("alice");
    }

    @Test
    void normalize_throwsForBlankUsername() {
        assertThatThrownBy(() -> usernameService.checkAvailability("   "))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void normalize_throwsForInvalidCharacters() {
        assertThatThrownBy(() -> usernameService.checkAvailability("alice!@#"))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void normalize_throwsForTooShortUsername() {
        assertThatThrownBy(() -> usernameService.checkAvailability("ab"))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void normalize_throwsForNull() {
        assertThatThrownBy(() -> usernameService.checkAvailability(null))
            .isInstanceOf(ValidationException.class);
    }
}
