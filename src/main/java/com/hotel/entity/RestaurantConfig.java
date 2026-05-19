package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "RESTAURANT_CONFIG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantConfig {

    // Always a single row — use a fixed PK of 1
    @Id
    @Column(nullable = false)
    @Builder.Default
    private Long id = 1L;

    // ── Business identity ─────────────────────────────────────────────────────
    @Column(nullable = false, length = 150)
    @Builder.Default
    private String restaurantName = "Rasoi Restaurant";

    @Column(length = 500)
    private String address;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 100)
    private String email;

    @Column(length = 50)
    private String gstNumber;

    @Column(length = 30)
    private String fssaiNumber;

    @Column(length = 200)
    private String websiteUrl;

    // ── Branding ──────────────────────────────────────────────────────────────
    @Column(length = 500)
    private String logoPath;

    @Column(length = 300)
    private String footerMessage;

    @Column(length = 300)
    @Builder.Default
    private String thankYouMessage = "Thank you for dining with us!";

    // ── Invoice / Tax ─────────────────────────────────────────────────────────
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String invoicePrefix = "INV";

    @Column(nullable = false, length = 5)
    @Builder.Default
    private String currencySymbol = "₹";

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultTaxPercent = new BigDecimal("18.00");

    // ── Audit ─────────────────────────────────────────────────────────────────
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
