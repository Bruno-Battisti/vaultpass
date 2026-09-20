package com.vaultpass.service;

import com.vaultpass.dto.mapper.UserSessionMapper;
import com.vaultpass.dto.security.UserSessionResponse;
import com.vaultpass.entity.UserSession;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.UserSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A "session" here just labels a refresh token for the user to recognize
 * ("Chrome on Windows, last used 2 hours ago") — revoking one always
 * revokes its underlying refresh token too, so it has real security effect,
 * not just cosmetic removal from a list.
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserSessionMapper userSessionMapper;

    @Transactional
    public void record(UUID userId, UUID refreshTokenId, String ip, String userAgent) {
        UserSession session = UserSession.builder()
                .userId(userId)
                .refreshTokenId(refreshTokenId)
                .ipAddress(ip)
                .userAgent(userAgent)
                .lastUsedAt(Instant.now())
                .build();
        userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> listActive(UUID userId) {
        return userSessionRepository.findAllByUserIdAndRevokedFalseOrderByLastUsedAtDesc(userId).stream()
                .map(userSessionMapper::toResponse)
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        UserSession session = userSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        session.setRevoked(true);
        userSessionRepository.save(session);
        if (session.getRefreshTokenId() != null) {
            refreshTokenService.revokeById(session.getRefreshTokenId());
        }
    }

    @Transactional
    public void revokeAll(UUID userId) {
        List<UserSession> active = userSessionRepository.findAllByUserIdAndRevokedFalseOrderByLastUsedAtDesc(userId);
        active.forEach(session -> session.setRevoked(true));
        userSessionRepository.saveAll(active);
        refreshTokenService.revokeAllForUser(userId);
    }
}
