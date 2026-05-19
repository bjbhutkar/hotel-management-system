package com.hotel.delivery.repository;

import com.hotel.delivery.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {
    Optional<WebhookLog> findByPayloadHash(String payloadHash);
    boolean existsByPayloadHash(String payloadHash);
}
