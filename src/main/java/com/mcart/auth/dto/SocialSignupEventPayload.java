package com.mcart.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Payload for social signup outbox events.
 * Consumed by downstream services (e.g. User Service) to create user profiles for social logins.
 */
@Getter
@Builder
public class SocialSignupEventPayload {

    private UUID userId;
    private String email;
    private String providerType;
    private String providerUserId;
}
