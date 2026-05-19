package com.hotel.delivery.repository;

import com.hotel.delivery.entity.MenuPlatformMapping;
import com.hotel.delivery.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuPlatformMappingRepository extends JpaRepository<MenuPlatformMapping, Long> {

    Optional<MenuPlatformMapping> findByMenuItemIdAndPlatformType(Long menuItemId, PlatformType platformType);

    List<MenuPlatformMapping> findByPlatformType(PlatformType platformType);

    Optional<MenuPlatformMapping> findByPlatformItemIdAndPlatformType(String platformItemId, PlatformType platformType);

    List<MenuPlatformMapping> findByMenuItemIdIn(List<Long> menuItemIds);
}
