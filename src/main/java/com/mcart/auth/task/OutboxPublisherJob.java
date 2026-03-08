package com.mcart.auth.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.model.OutboxStatus;
import com.mcart.auth.repository.OutBoxEventRepository;
import com.mcart.auth.service.VerificationEmailService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherJob {

    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String USER_SIGNUP = "USER_SIGNUP";

    private final OutBoxEventRepository outBoxEventRepository;
    private final VerificationEmailService verificationEmailService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventEntity> events =
                outBoxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEventEntity event : events) {
            try {
                switch (event.getAggregateType()) {
                    case EMAIL_VERIFICATION -> processVerificationEmail(event);
                    case USER_SIGNUP -> {
                        // USER_SIGNUP events are for downstream services via pub/sub.
                        // Skip until PubSubTemplate is configured - event stays PENDING.
                        continue;
                    }
                    default -> throw new IllegalStateException(
                            "Unknown aggregateType: " + event.getAggregateType());
                }
                event.markSent();
                outBoxEventRepository.save(event);
            } catch (Exception ex) {
                event.markFailed();
                outBoxEventRepository.save(event);
                log.warn("Failed to process outbox event {}", event.getId(), ex);
            }
        }
    }

    private void processVerificationEmail(OutboxEventEntity event) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
        String email = payload.get("email") != null ? payload.get("email").toString() : null;
        String token = payload.get("token") != null ? payload.get("token").toString() : null;
        if (email == null || token == null) {
            throw new IllegalArgumentException("Verification payload must contain email and token");
        }
        verificationEmailService.sendVerificationEmail(email, token);
    }
}
