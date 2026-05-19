package com.hotel.delivery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "PLATFORM_CREDENTIALS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false, unique = true)
    private DeliveryPlatform platform;

    @Column(length = 2000)
    private String encryptedApiKey;

    @Column(length = 2000)
    private String encryptedApiSecret;

    @Column(length = 4000)
    private String encryptedAccessToken;

    @Column(length = 4000)
    private String encryptedRefreshToken;

    @Column
    private LocalDateTime tokenExpiresAt;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
