package com.vaultpass.repository;

import com.vaultpass.entity.TwoFactorAuth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, UUID> {

    Optional<TwoFactorAuth> findByUserId(UUID userId);
}
