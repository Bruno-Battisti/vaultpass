package com.vaultpass.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vaultpass.util.ClientIpResolver;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        Cache<String, Bucket> cache = Caffeine.newBuilder().build();
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        when(clientIpResolver.resolve(any())).thenReturn("198.51.100.7");
        filter = new RateLimitingFilter(cache, clientIpResolver, JsonMapper.builder().build());
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    @Test
    void sixthLoginAttemptWithinAMinute_isRejectedWith429() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(loginRequest(), response, chain);
            verify(chain, times(1)).doFilter(any(), any());
        }

        MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
        FilterChain sixthChain = mock(FilterChain.class);
        filter.doFilter(loginRequest(), sixthResponse, sixthChain);

        assertThat(sixthResponse.getStatus()).isEqualTo(429);
        assertThat(sixthResponse.getHeader("Retry-After")).isNotNull();
        verify(sixthChain, never()).doFilter(any(), any());
    }

    @Test
    void disabled_neverBlocksEvenPastCapacity() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(loginRequest(), response, chain);
            verify(chain, times(1)).doFilter(any(), any());
        }
    }

    @Test
    void endpointWithoutRule_alwaysPassesThrough() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/vault");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            verify(chain, times(1)).doFilter(any(), any());
        }
    }

    private MockHttpServletRequest loginRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/auth/login");
    }
}
