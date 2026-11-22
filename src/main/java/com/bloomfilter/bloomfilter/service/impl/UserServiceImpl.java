package com.bloomfilter.bloomfilter.service.impl;

import com.bloomfilter.bloomfilter.bloom.BloomFilterStrategy;
import com.bloomfilter.bloomfilter.dto.UserCreateRequest;
import com.bloomfilter.bloomfilter.dto.UserCreateResponse;
import com.bloomfilter.bloomfilter.exception.UsernameAlreadyTakenException;
import com.bloomfilter.bloomfilter.model.User;
import com.bloomfilter.bloomfilter.repository.UserRepository;
import com.bloomfilter.bloomfilter.service.UsernameService;
import com.bloomfilter.bloomfilter.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String REDIS_PREFIX = "username:taken:";
    private static final Duration CACHE_TTL  = Duration.ofHours(24);

    private final UserRepository      userRepository;
    private final BloomFilterStrategy bloomFilter;
    private final StringRedisTemplate redisTemplate;
    private final UsernameService     usernameService;

    @Override
    @Transactional
    public UserCreateResponse createUser(UserCreateRequest request) {
        String username = usernameService.normalize(request.username());

        try {
            User user = User.builder()
                .username(username)
                .email(request.email())
                .build();

            User saved = userRepository.saveAndFlush(user);
            log.info("Registered user: id={} username={}", saved.getId(), username);

            // Update Bloom filter and Redis only AFTER the transaction commits successfully.
            // If we updated them inside the transaction and it later rolled back, the Bloom
            // filter (no deletes) and Redis would permanently show the username as taken.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bloomFilter.put(username);
                    redisTemplate.opsForValue().set(REDIS_PREFIX + username, "1", CACHE_TTL);
                    log.debug("Post-commit: Bloom+Redis updated for {}", username);
                }
            });

            return UserCreateResponse.from(saved);

        } catch (DataIntegrityViolationException ex) {
            // DB unique constraint fired — concurrent registration race was lost.
            // The Bloom filter may or may not contain this name yet; either way,
            // the DB is the source of truth.
            throw new UsernameAlreadyTakenException(username);
        }
    }
}
