package com.hotel.repository;

import com.hotel.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByAvailableTrue();

    java.util.Optional<MenuItem> findByNameIgnoreCase(String name);

    List<MenuItem> findByCategory(String category);

    List<MenuItem> findByCategoryAndAvailableTrue(String category);

    @Query("SELECT DISTINCT m.category FROM MenuItem m ORDER BY m.category")
    List<String> findAllCategories();
}
