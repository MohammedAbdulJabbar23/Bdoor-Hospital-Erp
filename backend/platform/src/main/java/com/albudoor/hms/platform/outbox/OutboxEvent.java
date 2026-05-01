package com.albudoor.hms.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String eventType;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column
    private Integer attemptCount;

    @Column(length = 1000)
    private String lastError;

    public enum Status { PENDING, PUBLISHED, FAILED }

    public static OutboxEvent pending(String eventType, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.eventType = eventType;
        e.payload = payload;
        e.occurredAt = Instant.now();
        e.status = Status.PENDING;
        e.attemptCount = 0;
        return e;
    }

    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.attemptCount = (attemptCount == null ? 0 : attemptCount) + 1;
        this.lastError = error;
    }
}
