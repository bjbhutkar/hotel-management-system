package com.hotel.delivery.entity;

import com.hotel.delivery.enums.OnlineOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ONLINE_ORDER_STATUS_HISTORY",
       indexes = @Index(name = "idx_status_history_order_id", columnList = "online_order_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnlineOrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "online_order_id", nullable = false)
    private OnlineOrder onlineOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OnlineOrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OnlineOrderStatus newStatus;

    @Column(length = 500)
    private String notes;

    @Column(length = 80)
    private String changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
