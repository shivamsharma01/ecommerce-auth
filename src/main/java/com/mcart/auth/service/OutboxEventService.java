package com.mcart.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcart.auth.dto.SocialSignupEventPayload;
import com.mcart.auth.dto.UserSignupEventPayload;
import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.entity.OutboxEventId;
import com.mcart.auth.model.OutboxStatus;
import com.mcart.auth.repository.OutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for persisting outbox events for pub/sub notifications.
 * Events are picked up by {@link com.mcart.auth.task.OutboxPublisherJob} and published
 * to topics consumed by downstream services (e.g. User Service).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    /** Aggregate type for user signup events. */
    public static final String USER_SIGNUP = "USER_SIGNUP";

    /** Event type when signup is complete (password or social). */
    public static final String USER_SIGNUP_COMPLETED = "USER_SIGNUP_COMPLETED";

    /** Event type when user completes /verify-email (password signup only). */
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";

    private final OutBoxEventRepository outBoxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    /**
     * Persists a USER_SIGNUP_COMPLETED event for password signup.
     * Downstream services use this to create user profiles.
     *
     * @param authIdentityId the auth identity ID (aggregate)
     * @param userId         the user ID
     * @param payload        the signup payload (email, firstName, lastName)
     * @throws JsonProcessingException if payload serialization fails
     */
    public void publishUserSignupEvent(UUID authIdentityId, UUID userId, UserSignupEventPayload payload)
            throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(payload);
        saveOutboxEvent(authIdentityId, userId, payloadJson);
    }

    /**
     * Persists a USER_SIGNUP_COMPLETED event for social signup.
     * Downstream services use this to create user profiles for social logins.
     *
     * @param authIdentityId the auth identity ID (aggregate)
     * @param userId         the user ID
     * @param payload        the social signup payload
     * @throws JsonProcessingException if payload serialization fails
     */
    public void publishSocialSignupEvent(UUID authIdentityId, UUID userId, SocialSignupEventPayload payload)
            throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(payload);
        saveOutboxEvent(authIdentityId, userId, payloadJson);
    }

    private void saveOutboxEvent(UUID authIdentityId, UUID userId, String payloadJson) {
        Instant now = Instant.now(clock);
        outBoxEventRepository.save(
                OutboxEventEntity.builder()
                        .id(new OutboxEventId(UUID.randomUUID(), authIdentityId))
                        .aggregateType(USER_SIGNUP)
                        .eventType(USER_SIGNUP_COMPLETED)
                        .userId(userId)
                        .retryCount(0)
                        .payload(payloadJson)
                        .status(OutboxStatus.PENDING)
                        .createdAt(now)
                        .lastAttemptAt(now)
                        .build()
        );
    }

    /**
     * Persists an EMAIL_VERIFIED event after successful /verify-email.
     * User service uses this to set verified=true so /me and /profile return profile details.
     *
     * @param authIdentityId the auth identity ID
     * @param userId         the user ID
     */
    public void publishEmailVerifiedEvent(UUID authIdentityId, UUID userId) throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(Map.of("userId", userId.toString()));
        Instant now = Instant.now(clock);
        outBoxEventRepository.save(
                OutboxEventEntity.builder()
                        .id(new OutboxEventId(UUID.randomUUID(), authIdentityId))
                        .aggregateType(USER_SIGNUP)
                        .eventType(EMAIL_VERIFIED)
                        .userId(userId)
                        .retryCount(0)
                        .payload(payloadJson)
                        .status(OutboxStatus.PENDING)
                        .createdAt(now)
                        .lastAttemptAt(now)
                        .build()
        );
    }
}
