package com.mcart.auth.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcart.auth.entity.OutboxEventEntity;
import com.mcart.auth.model.EmailStatus;
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

    private static final String TOPIC = "email-events";

    private final OutBoxEventRepository outBoxEventRepository;
   // private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {

        List<OutboxEventEntity> events =
                outBoxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(EmailStatus.PENDING);

        for (OutboxEventEntity event : events) {
            try {
                String message = buildMessage(event);

                //pubSubTemplate.publish(TOPIC, message);

                event.markSent();
            } catch (Exception ex) {
                event.markFailed();
                log.warn("Failed to publish outbox event {}", event.getId(), ex);
            }
        }
    }

    private String buildMessage(OutboxEventEntity event) throws Exception {

        Map<String, Object> payload = objectMapper.readValue(
                event.getPayload(), Map.class
        );

        Map<String, Object> message = Map.of(
                "eventType", event.getEventType(),
                "email", payload.get("email"),
                "token", payload.get("token"),
                "authIdentityId", event.getId().getAggregateId(),
                "createdAt", event.getCreatedAt().toString()
        );

        return objectMapper.writeValueAsString(message);
    }

}
