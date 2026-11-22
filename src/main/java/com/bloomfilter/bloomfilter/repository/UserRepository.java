package com.bloomfilter.bloomfilter.repository;

import com.bloomfilter.bloomfilter.model.User;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    /**
     * Stream all usernames for Bloom filter warmup.
     * Uses server-side cursor (fetchSize=1000) to avoid loading all rows into memory.
     * Must be called within a @Transactional(readOnly=true) context.
     */
    @Query("SELECT u.username FROM User u ORDER BY u.id")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "1000"),
        @QueryHint(name = "org.hibernate.readOnly",  value = "true")
    })
    Stream<String> streamAllUsernames();
}
