package com.hotel.delivery.entity;

import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ONLINE_ORDERS", indexes = {
        @Index(name = "idx_online_orders_platform_order_id", columnList = "platformOrderId"),
        @Index(name = "idx_online_orders_status",           columnList = "status"),
        @Index(name = "idx_online_orders_created_at",       columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "items")
public class OnlineOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String internalOrderNumber;

    @Column(nullable = false, length = 100)
    private String platformOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlatformType platformType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OnlineOrderStatus status = OnlineOrderStatus.NEW;

    // ── Customer ─────────────────────────────────────────────────────
    @Column(length = 100)
    private String customerName;

    @Column(length = 20)
    private String customerPhone;

    @Column(length = 500)
    private String deliveryAddress;

    // ── Financials ────────────────────────────────────────────────────
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal itemsTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(length = 30)
    private String paymentMode;

    @Column(nullable = false)
    @Builder.Default
    private boolean prepaid = false;

    // ── Delivery partner ──────────────────────────────────────────────
    @Column(length = 100)
    private String deliveryPartnerName;

    @Column(length = 20)
    private String deliveryPartnerPhone;

    // ── Operational ───────────────────────────────────────────────────
    @Column
    private Integer prepTimeMinutes;

    @Column(length = 500)
    private String rejectionReason;

    @Column(length = 500)
    private String customerNotes;

    // ── Linked internal order ─────────────────────────────────────────
    @Column
    private Long linkedInternalOrderId;

    // ── Timestamps ────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime placedAt;

    @Column
    private LocalDateTime acceptedAt;

    @Column
    private LocalDateTime readyAt;

    @Column
    private LocalDateTime deliveredAt;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "onlineOrder", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OnlineOrderItem> items = new ArrayList<>();

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
        if (!(o instanceof OnlineOrder other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
