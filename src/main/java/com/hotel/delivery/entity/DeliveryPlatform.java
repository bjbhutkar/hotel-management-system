package com.hotel.delivery.entity;

import com.hotel.delivery.enums.PlatformType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DELIVERY_PLATFORMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private PlatformType platformType;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column(length = 100)
    private String restaurantId;

    @Column(length = 255)
    private String webhookSecret;

    @Column(nullable = false)
    @Builder.Default
    private int pollIntervalSeconds = 30;

    @Column
    private LocalDateTime lastPolledAt;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "platform", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private PlatformCredential credential;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveryPlatform other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return displayName + " [" + platformType + ", active=" + active + "]";
    }
}
