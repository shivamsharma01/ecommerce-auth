package com.mcart.auth.entity;

import com.mcart.auth.model.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @EmbeddedId
    private OutboxEventId id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType; // AUTH_IDENTITY, ORDER, PAYMENT, EMAIL_VERIFICATION


    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false)
    private String eventType; // SEND_VERIFICATION_EMAIL

    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at", nullable = false)
    private Instant lastAttemptAt;

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.lastAttemptAt = Instant.now();
    }

    public void markFailed() {
        this.retryCount++;
        this.lastAttemptAt = Instant.now();
    }
}