package com.mcart.auth.entity;

import com.mcart.auth.model.AuthUserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserEntity {

    @Id
    @Column(name = "auth_identity_id")
    private UUID authIdentityId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthUserStatus status;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When true, access token includes scope {@code product.admin} for product / product-indexer admin APIs. */
    @Column(name = "platform_admin", nullable = false)
    @Builder.Default
    private boolean platformAdmin = false;
}
