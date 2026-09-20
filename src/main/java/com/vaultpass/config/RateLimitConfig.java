package com.vaultpass.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    /**
     * In-memory only — buckets are lost on restart and not shared across
     * instances. Fine for a single-node deployment; a multi-replica
     * deployment would need a shared store (e.g. bucket4j-redis).
     */
    @Bean
    public Cache<String, Bucket> rateLimitBucketCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(1))
                .maximumSize(100_000)
                .build();
    }
}
