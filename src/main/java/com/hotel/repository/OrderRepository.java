package com.hotel.repository;

import com.hotel.entity.Order;
import com.hotel.entity.Order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByStatusIn(List<OrderStatus> statuses);

    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<Order> findByStatusAndCreatedAtBetween(OrderStatus status, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(o.orderNumber, 14) AS int)), 0) FROM Order o " +
           "WHERE o.orderNumber LIKE CONCAT('ORD-', :datePart, '-%')")
    int findMaxCounterForDate(@Param("datePart") String datePart);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.menuItem WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
           "WHERE o.status = 'BILLED' AND o.createdAt BETWEEN :start AND :end")
    BigDecimal sumRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.status = 'CANCELLED' AND o.createdAt BETWEEN :start AND :end")
    Long countCancelledByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    long countByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);
}
