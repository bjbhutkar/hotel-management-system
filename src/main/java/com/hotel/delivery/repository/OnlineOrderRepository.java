package com.hotel.delivery.repository;

import com.hotel.delivery.entity.OnlineOrder;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OnlineOrderRepository extends JpaRepository<OnlineOrder, Long> {

    Optional<OnlineOrder> findByPlatformOrderIdAndPlatformType(String platformOrderId, PlatformType platformType);

    boolean existsByPlatformOrderIdAndPlatformType(String platformOrderId, PlatformType platformType);

    List<OnlineOrder> findByStatusNotInOrderByCreatedAtDesc(List<OnlineOrderStatus> excludedStatuses);

    List<OnlineOrder> findByStatusInOrderByCreatedAtDesc(List<OnlineOrderStatus> statuses);

    List<OnlineOrder> findByPlatformTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            PlatformType platformType, LocalDateTime from, LocalDateTime to);

    List<OnlineOrder> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    @Query("SELECT o FROM OnlineOrder o WHERE o.status = com.hotel.delivery.enums.OnlineOrderStatus.NEW ORDER BY o.createdAt ASC")
    List<OnlineOrder> findNewOrdersOldestFirst();

    long countByStatus(OnlineOrderStatus status);

    long countByPlatformTypeAndCreatedAtBetween(PlatformType platformType, LocalDateTime from, LocalDateTime to);
}
