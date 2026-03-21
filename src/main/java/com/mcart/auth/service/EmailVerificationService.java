package com.mcart.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcart.auth.entity.AuthIdentityEntity;
import com.mcart.auth.entity.EmailVerificationEntity;
import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.entity.OutboxEventId;
import com.mcart.auth.exception.UnauthorizedException;
import com.mcart.auth.model.AuthProviderType;
import com.mcart.auth.model.OutboxStatus;
import com.mcart.auth.repository.AuthIdentityRepository;
import com.mcart.auth.repository.EmailVerificationRepository;
import com.mcart.auth.repository.OutBoxEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String SEND_VERIFICATION_EMAIL = "SEND_VERIFICATION_EMAIL";

    @Value("${auth.verification.token-ttl-hours:24}")
    private int tokenTtlHours;

    private final EmailVerificationRateLimiter emailVerificationRateLimiter;
    private final ObjectMapper objectMapper;
    private final AuthIdentityRepository authIdentityRepo;
    private final OutBoxEventRepository outBoxEventRepository;
    private final OutboxEventService outboxEventService;
    private final EmailVerificationRepository emailVerificationRepo;
    private final Clock clock = Clock.systemUTC();

    public void issueVerification(UUID authIdentityId, UUID userId, String email) throws JsonProcessingException {
        EmailVerificationEntity verification = EmailVerificationEntity.builder()
                .verificationId(UUID.randomUUID())
                .authIdentityId(authIdentityId)
                .email(email)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now(clock).plus(tokenTtlHours, ChronoUnit.HOURS))
                .build();

        // 🔁 Rate limit hook (Redis)
        emailVerificationRateLimiter.assertAllowed(authIdentityId);

        emailVerificationRepo.save(verification);

        String payload = objectMapper.writeValueAsString(
                Map.of(
                        "email", verification.getEmail(),
                        "token", verification.getToken()
                )
        );

        Instant now = Instant.now(clock);
        outBoxEventRepository.save(
                OutboxEventEntity.builder()
                        .id(new OutboxEventId(UUID.randomUUID(), authIdentityId))
                        .aggregateType(EMAIL_VERIFICATION)
                        .eventType(SEND_VERIFICATION_EMAIL)
                        .userId(userId)
                        .retryCount(0)
                        .payload(payload)
                        .status(OutboxStatus.PENDING)
                        .createdAt(now)
                        .lastAttemptAt(now)
                        .build()
        );
    }

    @Transactional
    public void verifyEmail(String token) {

        EmailVerificationEntity verification = emailVerificationRepo
                .findByToken(token)
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid or expired verification link")
                );

        if (verification.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new UnauthorizedException("Verification link expired");
        }

        AuthIdentityEntity identity = authIdentityRepo
                .findById(verification.getAuthIdentityId())
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid verification request")
                );

        if (identity.isEmailVerified()) {
            // idempotent behavior
            emailVerificationRepo.delete(verification);
            return;
        }

        // mark verified
        identity.setEmailVerified(true);
        identity.setEmailVerifiedAt(Instant.now(clock));
        authIdentityRepo.save(identity);

        // publish EMAIL_VERIFIED for user service (sets verified=true so /me and /profile work)
        try {
            outboxEventService.publishEmailVerifiedEvent(identity.getAuthIdentityId(), identity.getUserId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to publish email verified event", e);
        }

        // delete token (single-use)
        emailVerificationRepo.delete(verification);
    }

    @Transactional
    public void resendVerification(String email) {

        // lookup PASSWORD identity
        authIdentityRepo
                .findByProviderTypeAndIdentifier(
                        AuthProviderType.PASSWORD,
                        email
                )
                .ifPresent(identity -> {

                    if (identity.isEmailVerified()) {
                        return; // silent exit
                    }

                    // invalidate existing tokens
                    emailVerificationRepo.deleteByAuthIdentityId(identity.getAuthIdentityId());

                    // issue new token
                    try {
                        issueVerification(identity.getAuthIdentityId(), identity.getUserId(), identity.getEmail());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
