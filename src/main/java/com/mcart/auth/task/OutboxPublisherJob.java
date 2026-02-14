package com.mcart.auth.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.model.OutboxStatus;
import com.mcart.auth.repository.OutBoxEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
//import com.google.cloud.spring.pubsub.core.PubSubTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherJob {

    private static final String EMAIL_TOPIC = "email-events";
    private static final String USER_SIGNUP_TOPIC = "user-signup-events";

    private final OutBoxEventRepository outBoxEventRepository;
   // private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {

        List<OutboxEventEntity> events =
                outBoxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEventEntity event : events) {
            try {
                String message = buildMessage(event);
                String topic = resolveTopic(event);

                //pubSubTemplate.publish(EMAIL_TOPIC, message);

                event.markSent();
            } catch (Exception ex) {
                event.markFailed();
                log.warn("Failed to publish outbox event {}", event.getId(), ex);
            }
        }
    }


    private String resolveTopic(OutboxEventEntity event) {
        return switch (event.getAggregateType()) {
            case "EMAIL_VERIFICATION" -> EMAIL_TOPIC;
            case "USER_SIGNUP" -> USER_SIGNUP_TOPIC;
            default -> throw new IllegalStateException(
                    "Unknown aggregateType: " + event.getAggregateType()
            );
        };
    }

    private String buildMessage(OutboxEventEntity event) throws Exception {

        Map<String, Object> payload = objectMapper.readValue(
                event.getPayload(), Map.class
        );

        return objectMapper.writeValueAsString(
                Map.of(
                        "eventType", event.getEventType(),
                        "aggregateType", event.getAggregateType(),
                        "userId", event.getUserId(),
                        "authIdentityId", event.getId().getAggregateId(),
                        "payload", payload,
                        "occurredAt", event.getCreatedAt().toString(),
                        "version", 1
                )
        );
    }

}
