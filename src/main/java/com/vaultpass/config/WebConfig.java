package com.vaultpass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class WebConfig {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Without this, a client could request e.g. ?size=999999 on any
     * paginated endpoint and force an unbounded query. Spring Data clamps
     * silently to this ceiling instead of erroring.
     */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
    }
}
