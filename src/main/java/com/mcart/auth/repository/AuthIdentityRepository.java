package com.mcart.auth.repository;

import com.mcart.auth.entity.AuthIdentityEntity;
import com.mcart.auth.model.AuthProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthIdentityRepository extends JpaRepository<AuthIdentityEntity, UUID> {

    // Password login
    Optional<AuthIdentityEntity> findByProviderTypeAndIdentifier(
            AuthProviderType providerType,
            String identifier
    );

    // Social login lookup
    Optional<AuthIdentityEntity> findByProviderTypeAndProviderUserId(
            AuthProviderType providerType,
            String providerUserId
    );

    // Fetch all identities of a user (future-proof)
    List<AuthIdentityEntity> findAllByUserId(UUID userId);

    // Used for email verification
    Optional<AuthIdentityEntity> findByAuthIdentityId(UUID authIdentityId);
}
