package com.hotel.repository;

import com.hotel.entity.RestaurantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantConfigRepository extends JpaRepository<RestaurantConfig, Long> {
}
