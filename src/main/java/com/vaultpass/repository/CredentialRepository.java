package com.vaultpass.repository;

import com.vaultpass.entity.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByIdAndUserId(UUID id, UUID userId);

    Page<Credential> findAllByUserId(UUID userId, Pageable pageable);
}
