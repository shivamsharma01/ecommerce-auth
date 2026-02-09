package com.mcart.auth.repository;

import com.mcart.auth.entity.EmailVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository
        extends JpaRepository<EmailVerificationEntity, UUID> {

    Optional<EmailVerificationEntity> findByToken(String token);

    @Modifying
    @Query("""
        delete from EmailVerificationEntity e
        where e.expiresAt < :now
    """)
    void deleteExpired(@Param("now") Instant now);

    void deleteByAuthIdentityId(UUID authIdentityId);

    long deleteByExpiresAtBefore(Instant now);
}
