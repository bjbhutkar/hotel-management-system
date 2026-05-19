package com.hotel.service;

import com.hotel.entity.MenuItem;
import com.hotel.entity.Order;
import com.hotel.entity.Order.OrderStatus;
import com.hotel.entity.OrderItem;
import com.hotel.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final AtomicInteger orderCounter = new AtomicInteger(0);
    private String counterDate = "";

    @Transactional
    public Order createOrder(int tableNumber, String customerName) {
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .tableNumber(tableNumber)
                .customerName(customerName)
                .status(OrderStatus.PENDING)
                .createdBy(userService.getCurrentUser())
                .build();
        Order saved = orderRepository.save(order);
        log.info("Created order {} for table {}", saved.getOrderNumber(), tableNumber);
        return saved;
    }

    @Transactional
    public Order addItemToOrder(Long orderId, MenuItem menuItem, int quantity, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // If item already exists, increment quantity
        Optional<OrderItem> existing = order.getItems().stream()
                .filter(oi -> oi.getMenuItem().getId().equals(menuItem.getId()))
                .findFirst();

        if (existing.isPresent()) {
            OrderItem oi = existing.get();
            oi.setQuantity(oi.getQuantity() + quantity);
            oi.setTotalPrice(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
        } else {
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .menuItem(menuItem)
                    .quantity(quantity)
                    .unitPrice(menuItem.getPrice())
                    .totalPrice(menuItem.getPrice().multiply(BigDecimal.valueOf(quantity)))
                    .notes(notes)
                    .build();
            order.getItems().add(item);
        }

        recalculateTotals(order);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.IN_PROGRESS);
        }
        return orderRepository.save(order);
    }

    @Transactional
    public Order removeItemFromOrder(Long orderId, Long orderItemId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.getItems().removeIf(oi -> oi.getId().equals(orderItemId));
        recalculateTotals(order);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        log.info("Order {} cancelled. Reason: {}", order.getOrderNumber(), reason);
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateTaxRate(Long orderId, BigDecimal taxRate) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setTaxRate(taxRate);
        recalculateTotals(order);
        return orderRepository.save(order);
    }

    @Transactional
    public Order reopenOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(OrderStatus.IN_PROGRESS);
        log.info("Order {} reopened for editing.", order.getOrderNumber());
        return orderRepository.save(order);
    }

    @Transactional
    public Order completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional
    public Order markAsBilled(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(OrderStatus.BILLED);
        if (order.getCompletedAt() == null) {
            order.setCompletedAt(LocalDateTime.now());
        }
        log.info("Order {} marked as billed. Total: ₹{}", order.getOrderNumber(), order.getTotalAmount());
        return orderRepository.save(order);
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderWithDetails(Long id) {
        return orderRepository.findByIdWithDetails(id);
    }

    public List<Order> getActiveOrders() {
        return orderRepository.findByStatusIn(
                List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.BILLED));
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByDateRange(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void recalculateTotals(Order order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);

        BigDecimal taxAmount = subtotal
                .multiply(order.getTaxRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        order.setTaxAmount(taxAmount);
        order.setTotalAmount(subtotal.add(taxAmount));
    }

    private synchronized String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!datePart.equals(counterDate)) {
            // New day (or first call): seed counter from DB so restarts never collide
            int max = orderRepository.findMaxCounterForDate(datePart);
            orderCounter.set(max + 1);
            counterDate = datePart;
        }
        return "ORD-" + datePart + "-" + String.format("%04d", orderCounter.getAndIncrement());
    }
}
