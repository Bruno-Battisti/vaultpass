package com.vaultpass.repository;

import com.vaultpass.entity.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Native ILIKE (not JPQL's LOWER()+LIKE) so Postgres can use the
     * gin_trgm_ops indexes on title/username defined in V6 — those only
     * accelerate LIKE/ILIKE against the raw column, not a wrapped lower(title).
     */
    @Query(value = """
            SELECT * FROM credentials c
            WHERE c.user_id = :userId
              AND (CAST(:categoryId AS uuid) IS NULL OR c.category_id = CAST(:categoryId AS uuid))
              AND (:search IS NULL
                   OR c.title ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR c.username ILIKE CONCAT('%', CAST(:search AS text), '%'))
            """,
            countQuery = """
            SELECT count(*) FROM credentials c
            WHERE c.user_id = :userId
              AND (CAST(:categoryId AS uuid) IS NULL OR c.category_id = CAST(:categoryId AS uuid))
              AND (:search IS NULL
                   OR c.title ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR c.username ILIKE CONCAT('%', CAST(:search AS text), '%'))
            """,
            nativeQuery = true)
    Page<Credential> search(@Param("userId") UUID userId,
                             @Param("categoryId") UUID categoryId,
                             @Param("search") String search,
                             Pageable pageable);
}
