package com.hotel.delivery.entity;

import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.enums.RetryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "RETRY_QUEUE",
       indexes = {
           @Index(name = "idx_retry_queue_status",      columnList = "status"),
           @Index(name = "idx_retry_queue_next_retry",  columnList = "nextRetryAt")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlatformType platformType;

    @Column(nullable = false, length = 60)
    private String operationType;

    @Column(nullable = false, length = 100)
    private String platformOrderId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RetryStatus status = RetryStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column
    private LocalDateTime nextRetryAt;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (nextRetryAt == null) nextRetryAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
