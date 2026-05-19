package com.hotel.delivery.entity;

import com.hotel.delivery.enums.PlatformType;
import com.hotel.entity.MenuItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "MENU_PLATFORM_MAPPINGS",
       uniqueConstraints = @UniqueConstraint(columnNames = {"menuItem_id", "platformType"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuPlatformMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menuItem_id", nullable = false)
    private MenuItem menuItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlatformType platformType;

    @Column(nullable = false, length = 100)
    private String platformItemId;

    @Column(length = 100)
    private String platformCategoryId;

    @Column(nullable = false)
    @Builder.Default
    private boolean syncEnabled = true;

    @Column
    private LocalDateTime lastSyncedAt;

    @Column
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuPlatformMapping other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
