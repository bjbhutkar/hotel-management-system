package com.hotel.repository;

import com.hotel.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.menuItem.name, SUM(oi.quantity) AS totalQty " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE o.createdAt BETWEEN :start AND :end AND o.status = 'BILLED' " +
           "GROUP BY oi.menuItem.id, oi.menuItem.name ORDER BY totalQty DESC")
    List<Object[]> findMostSoldItems(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("SELECT oi.menuItem.name, SUM(oi.quantity) AS totalQty " +
           "FROM OrderItem oi JOIN oi.order o " +
           "WHERE o.createdAt BETWEEN :start AND :end AND o.status = 'BILLED' " +
           "GROUP BY oi.menuItem.id, oi.menuItem.name ORDER BY totalQty ASC")
    List<Object[]> findLeastSoldItems(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);
}
