package com.hotel.delivery.repository;

import com.hotel.delivery.entity.OnlineOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OnlineOrderStatusHistoryRepository extends JpaRepository<OnlineOrderStatusHistory, Long> {
    List<OnlineOrderStatusHistory> findByOnlineOrderIdOrderByChangedAtAsc(Long onlineOrderId);
}
