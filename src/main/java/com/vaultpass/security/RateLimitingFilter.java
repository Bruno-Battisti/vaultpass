package com.vaultpass.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.vaultpass.dto.common.ApiErrorResponse;
import com.vaultpass.util.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Per-IP, per-endpoint token bucket. Only the auth endpoints most exposed to
 * brute-force/enumeration are limited — everything else is left to the
 * (much more precise) per-user rate limiting a paid tier might add later.
 * Disabled via security.rate-limit.enabled in the test profile: IT tests
 * share one Spring context (and bucket cache) across many @Test methods
 * hitting /auth/login from the same synthetic IP, which would otherwise
 * make unrelated tests fail once the shared bucket runs dry.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Map<String, RateLimitRule> RULES = Map.of(
            "/api/v1/auth/login", new RateLimitRule(5, Duration.ofMinutes(1)),
            "/api/v1/auth/register", new RateLimitRule(3, Duration.ofHours(1)),
            "/api/v1/auth/refresh", new RateLimitRule(20, Duration.ofMinutes(1))
    );

    private final Cache<String, Bucket> rateLimitBucketCache;
    private final ClientIpResolver clientIpResolver;
    private final JsonMapper jsonMapper;

    @Value("${security.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitRule rule = enabled ? RULES.get(request.getRequestURI()) : null;
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRequestURI() + ':' + clientIpResolver.resolve(request);
        Bucket bucket = rateLimitBucketCache.get(key, k -> newBucket(rule));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(rule.period().toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded, try again later",
                request.getRequestURI(),
                null);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    private Bucket newBucket(RateLimitRule rule) {
        Bandwidth limit = Bandwidth.classic(rule.capacity(), Refill.greedy(rule.capacity(), rule.period()));
        return Bucket.builder().addLimit(limit).build();
    }

    private record RateLimitRule(int capacity, Duration period) {
    }
}
