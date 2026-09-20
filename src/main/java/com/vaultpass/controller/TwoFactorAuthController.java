package com.vaultpass.controller;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.security.TwoFactorDisableRequest;
import com.vaultpass.dto.security.TwoFactorEnableRequest;
import com.vaultpass.dto.security.TwoFactorEnableResponse;
import com.vaultpass.dto.security.TwoFactorSetupResponse;
import com.vaultpass.dto.security.TwoFactorVerifyRequest;
import com.vaultpass.security.CustomUserPrincipal;
import com.vaultpass.service.TwoFactorAuthService;
import com.vaultpass.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(twoFactorAuthService.setup(principal.getUserId()));
    }

    @PostMapping("/enable")
    public ResponseEntity<TwoFactorEnableResponse> enable(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                            @Valid @RequestBody TwoFactorEnableRequest request) {
        return ResponseEntity.ok(twoFactorAuthService.enable(principal.getUserId(), request.code()));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal CustomUserPrincipal principal,
                                         @Valid @RequestBody TwoFactorDisableRequest request) {
        twoFactorAuthService.disable(principal.getUserId(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody TwoFactorVerifyRequest request, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        return ResponseEntity.ok(twoFactorAuthService.verify(request, ip, userAgent));
    }
}
