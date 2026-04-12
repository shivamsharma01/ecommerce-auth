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

    public void publishUserSignupEvent(UUID authIdentityId, UUID userId, UserSignupEventPayload payload)
            throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(payload);
        saveOutboxEvent(authIdentityId, userId, payloadJson);
    }

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
        log.debug("Auth outbox queued eventType={} userId={}", USER_SIGNUP_COMPLETED, userId);
    }

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
        log.debug("Auth outbox queued eventType={} userId={}", EMAIL_VERIFIED, userId);
    }
}
