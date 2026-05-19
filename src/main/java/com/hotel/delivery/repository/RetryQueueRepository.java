package com.hotel.delivery.repository;

import com.hotel.delivery.entity.RetryQueueItem;
import com.hotel.delivery.enums.RetryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RetryQueueRepository extends JpaRepository<RetryQueueItem, Long> {
    List<RetryQueueItem> findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            RetryStatus status, LocalDateTime now);
}
