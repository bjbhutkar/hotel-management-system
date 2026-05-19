package com.hotel.delivery.repository;

import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryPlatformRepository extends JpaRepository<DeliveryPlatform, Long> {
    Optional<DeliveryPlatform> findByPlatformType(PlatformType platformType);
    List<DeliveryPlatform> findByActiveTrue();
}
