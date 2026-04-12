package com.mcart.auth.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.model.OutboxStatus;
import com.mcart.auth.repository.OutBoxEventRepository;
import com.mcart.auth.service.OutboxEventService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OutboxPublisherJob {

    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String USER_SIGNUP = "USER_SIGNUP";
    private static final int EVENT_VERSION = 1;

    private final OutBoxEventRepository outBoxEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private PubSubTemplate pubSubTemplate;

    @Value("${auth.pubsub.user-signup-topic:user-signup-events}")
    private String userSignupTopic;

    @Value("${auth.pubsub.email-verification-topic:email-verification-events}")
    private String emailVerificationTopic;

    public OutboxPublisherJob(OutBoxEventRepository outBoxEventRepository, ObjectMapper objectMapper) {
        this.outBoxEventRepository = outBoxEventRepository;
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEventEntity> events =
                outBoxEventRepository.findPendingForPublish(OutboxStatus.PENDING.name());

        for (OutboxEventEntity event : events) {
            try {
                boolean processed = switch (event.getAggregateType()) {
                    case EMAIL_VERIFICATION -> publishVerificationEmailEvent(event);
                    case USER_SIGNUP -> processUserSignupEvent(event);
                    default -> throw new IllegalStateException(
                            "Unknown aggregateType: " + event.getAggregateType());
                };
                if (processed) {
                    event.markSent();
                    outBoxEventRepository.save(event);
                }
                // When false: Pub/Sub not configured, event stays PENDING for next run
            } catch (Exception ex) {
                event.markFailed();
                outBoxEventRepository.save(event);
                log.warn("Failed to process outbox event {}", event.getId(), ex);
            }
        }
    }

    private boolean processUserSignupEvent(OutboxEventEntity event) throws Exception {
        if (pubSubTemplate == null) {
            log.debug("PubSubTemplate not available, skipping USER_SIGNUP event (stays PENDING)");
            return false;
        }

        Map<String, Object> message;
        if (OutboxEventService.EMAIL_VERIFIED.equals(event.getEventType())) {
            message = Map.of(
                    "eventType", event.getEventType(),
                    "aggregateType", event.getAggregateType(),
                    "userId", event.getUserId().toString(),
                    "authIdentityId", event.getId().getAggregateId().toString(),
                    "payload", objectMapper.readValue(event.getPayload(), Map.class),
                    "occurredAt", event.getCreatedAt().toString(),
                    "version", EVENT_VERSION
            );
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(event.getPayload(), Map.class);
            String email = getString(payloadMap, "email");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("User signup payload must contain email");
            }
            Map<String, Object> normalizedPayload = new HashMap<>(payloadMap);
            normalizedPayload.putIfAbsent("firstName", "");
            normalizedPayload.putIfAbsent("lastName", "");
            message = Map.of(
                    "eventType", event.getEventType(),
                    "aggregateType", event.getAggregateType(),
                    "userId", event.getUserId().toString(),
                    "authIdentityId", event.getId().getAggregateId().toString(),
                    "payload", normalizedPayload,
                    "occurredAt", event.getCreatedAt().toString(),
                    "version", EVENT_VERSION
            );
        }

        String messageJson = objectMapper.writeValueAsString(message);
        pubSubTemplate.publish(userSignupTopic, messageJson);
        log.info("Published {} event for userId={} to topic {}", event.getEventType(), event.getUserId(), userSignupTopic);
        return true;
    }

    private boolean publishVerificationEmailEvent(OutboxEventEntity event) throws Exception {
        if (pubSubTemplate == null) {
            log.debug("PubSubTemplate not available, skipping EMAIL_VERIFICATION event (stays PENDING)");
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
        String email = payload.get("email") != null ? payload.get("email").toString() : null;
        String token = payload.get("token") != null ? payload.get("token").toString() : null;
        if (email == null || token == null) {
            throw new IllegalArgumentException("Verification payload must contain email and token");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", event.getEventType());
        message.put("aggregateType", event.getAggregateType());
        message.put("userId", event.getUserId().toString());
        message.put("authIdentityId", event.getId().getAggregateId().toString());
        message.put("payload", payload);
        message.put("occurredAt", event.getCreatedAt().toString());
        message.put("version", EVENT_VERSION);
        message.put("outboxEventId", event.getId().getId().toString());

        String messageJson = objectMapper.writeValueAsString(message);
        pubSubTemplate.publish(emailVerificationTopic, messageJson);
        log.info("Published {} for userId={} to topic {}", event.getEventType(), event.getUserId(), emailVerificationTopic);
        return true;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
