package com.mcart.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Payload for USER_SIGNUP / USER_SIGNUP_COMPLETED outbox events.
 * Consumed by downstream services (e.g. User Service) to create user profiles.
 */
@Getter
@Builder
public class UserSignupEventPayload {

    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String providerType;
}
