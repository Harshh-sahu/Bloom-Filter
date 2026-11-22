package com.bloomfilter.bloomfilter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Context-load smoke test.
 *
 * Uses H2 in-memory (no PostgreSQL container) and mocks StringRedisTemplate
 * so no live Redis is required.  Pool is disabled to prevent eager connection
 * attempts; the mock replaces the template bean used by services.
 */
@SpringBootTest
@TestPropertySource(properties = {
    // H2 in PostgreSQL-compatibility mode
    "spring.datasource.url=jdbc:h2:mem:bloomtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    // Auto-detect dialect for H2 (overrides application.yml's PostgreSQLDialect)
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    // Redis: point at a non-existent host; pool disabled so no eager connect
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6399",
    "spring.data.redis.lettuce.pool.enabled=false",
    // Disable Bloom filter warmup (needs a live DB with data)
    "bloom-filter.warmup-on-startup=false",
    "bloom-filter.expected-insertions=1000",
    "bloom-filter.false-positive-probability=0.01"
})
class BloomfilterApplicationTests {

    // Replace the auto-configured StringRedisTemplate with a Mockito mock.
    // This prevents any actual Redis IO even if the connection factory is wired.
    @MockBean
    StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
        // The Spring application context must start without errors.
        // This verifies bean wiring, @ConfigurationProperties binding, and JPA schema creation.
    }
}
