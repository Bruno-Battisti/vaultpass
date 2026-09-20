package com.vaultpass.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * X-Forwarded-For is attacker-controllable unless stripped/overwritten by a
 * trusted reverse proxy in front of the app — this project has none
 * configured, so this is a best-effort resolver, not a hardened one. Behind
 * a real proxy, only trust this header after validating the proxy chain.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
