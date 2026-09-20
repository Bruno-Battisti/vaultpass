package com.vaultpass.repository;

import com.vaultpass.entity.TwoFactorRecoveryCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoFactorRecoveryCodeRepository extends JpaRepository<TwoFactorRecoveryCode, UUID> {

    Optional<TwoFactorRecoveryCode> findByTwoFactorAuthIdAndCodeHashAndUsedAtIsNull(UUID twoFactorAuthId, String codeHash);

    void deleteAllByTwoFactorAuthId(UUID twoFactorAuthId);
}
