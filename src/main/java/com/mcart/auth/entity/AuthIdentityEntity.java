package com.mcart.auth.entity;

import com.mcart.auth.model.AuthProviderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "auth_identity",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider_type", "provider_user_id"}),
                @UniqueConstraint(columnNames = {"provider_type", "identifier"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthIdentityEntity {

    @Id
    @Column(name = "auth_identity_id")
    private UUID authIdentityId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private AuthProviderType providerType; // PASSWORD | GOOGLE | FACEBOOK | APPLE

    @Column(name = "provider_user_id")
    private String providerUserId; // NULL for PASSWORD

    @Column(name = "identifier")
    private String identifier; // username or email

    @Column(name = "password_hash")
    private String passwordHash; // only for PASSWORD

    @Column(name = "email")
    private String email; // from provider or signup

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}