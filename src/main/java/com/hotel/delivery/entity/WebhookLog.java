package com.hotel.delivery.entity;

import com.hotel.delivery.enums.PlatformType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "WEBHOOK_LOGS",
       indexes = @Index(name = "idx_webhook_logs_received_at", columnList = "receivedAt"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlatformType platformType;

    @Column(length = 100)
    private String eventType;

    @Column(length = 100)
    private String platformOrderId;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Column(length = 64)
    private String payloadHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(length = 500)
    private String processingError;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
