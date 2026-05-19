package com.hotel.delivery.service;

import com.hotel.delivery.dto.IncomingOrderDto;
import com.hotel.delivery.dto.IncomingOrderItemDto;
import com.hotel.delivery.entity.*;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.event.NewOnlineOrderEvent;
import com.hotel.delivery.event.OrderStatusChangedEvent;
import com.hotel.delivery.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineOrderProcessingService {

    private final OnlineOrderRepository              onlineOrderRepository;
    private final OnlineOrderStatusHistoryRepository statusHistoryRepository;
    private final ApplicationEventPublisher          eventPublisher;

    private final AtomicInteger onlineCounter = new AtomicInteger(1);
    private String              counterDate   = "";

    // ── Ingest ───────────────────────────────────────────────────────────────

    @Transactional
    public Optional<OnlineOrder> processIncomingOrder(IncomingOrderDto dto) {
        // Idempotency guard — never create duplicates
        if (onlineOrderRepository.existsByPlatformOrderIdAndPlatformType(
                dto.getPlatformOrderId(), dto.getPlatformType())) {
            log.debug("Duplicate order {} from {} — skipped",
                    dto.getPlatformOrderId(), dto.getPlatformType());
            return Optional.empty();
        }

        OnlineOrder order = buildOnlineOrder(dto);
        order = onlineOrderRepository.save(order);

        recordHistory(order, null, OnlineOrderStatus.NEW, "Order received from " + dto.getPlatformType());

        log.info("New online order {} from {} — ₹{} ({})",
                order.getInternalOrderNumber(), order.getPlatformType(),
                order.getGrandTotal(), order.getCustomerName());

        eventPublisher.publishEvent(new NewOnlineOrderEvent(this, order));
        return Optional.of(order);
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Transactional
    public OnlineOrder acceptOrder(Long onlineOrderId, int prepTimeMinutes) {
        OnlineOrder order = fetchOrThrow(onlineOrderId);
        OnlineOrderStatus prev = order.getStatus();
        order.setStatus(OnlineOrderStatus.ACCEPTED);
        order.setPrepTimeMinutes(prepTimeMinutes);
        order.setAcceptedAt(LocalDateTime.now());
        order = onlineOrderRepository.save(order);
        recordHistory(order, prev, OnlineOrderStatus.ACCEPTED,
                "Accepted with prep time " + prepTimeMinutes + " min");
        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, order, prev, OnlineOrderStatus.ACCEPTED));
        return order;
    }

    @Transactional
    public OnlineOrder rejectOrder(Long onlineOrderId, String reason) {
        OnlineOrder order = fetchOrThrow(onlineOrderId);
        OnlineOrderStatus prev = order.getStatus();
        order.setStatus(OnlineOrderStatus.REJECTED);
        order.setRejectionReason(reason);
        order = onlineOrderRepository.save(order);
        recordHistory(order, prev, OnlineOrderStatus.REJECTED, "Rejected: " + reason);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, order, prev, OnlineOrderStatus.REJECTED));
        return order;
    }

    @Transactional
    public OnlineOrder updateStatus(Long onlineOrderId, OnlineOrderStatus newStatus) {
        OnlineOrder order = fetchOrThrow(onlineOrderId);
        OnlineOrderStatus prev = order.getStatus();
        order.setStatus(newStatus);
        if (newStatus == OnlineOrderStatus.READY)    order.setReadyAt(LocalDateTime.now());
        if (newStatus == OnlineOrderStatus.DELIVERED) order.setDeliveredAt(LocalDateTime.now());
        order = onlineOrderRepository.save(order);
        recordHistory(order, prev, newStatus, "Status updated");
        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, order, prev, newStatus));
        return order;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<OnlineOrder> getActiveOrders() {
        return onlineOrderRepository.findByStatusInOrderByCreatedAtDesc(List.of(
                OnlineOrderStatus.NEW,
                OnlineOrderStatus.ACCEPTED,
                OnlineOrderStatus.PREPARING,
                OnlineOrderStatus.READY));
    }

    public List<OnlineOrder> getAllOrdersForDashboard() {
        return onlineOrderRepository.findByStatusNotInOrderByCreatedAtDesc(List.of(
                OnlineOrderStatus.DELIVERED, OnlineOrderStatus.CANCELLED, OnlineOrderStatus.REJECTED));
    }

    public List<OnlineOrder> getOrderHistory(LocalDateTime from, LocalDateTime to) {
        return onlineOrderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    public Optional<OnlineOrder> findById(Long id) {
        return onlineOrderRepository.findById(id);
    }

    public long countNewOrders() {
        return onlineOrderRepository.countByStatus(OnlineOrderStatus.NEW);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OnlineOrder buildOnlineOrder(IncomingOrderDto dto) {
        List<OnlineOrderItem> items = new ArrayList<>();
        OnlineOrder order = OnlineOrder.builder()
                .internalOrderNumber(generateOrderNumber())
                .platformOrderId(dto.getPlatformOrderId())
                .platformType(dto.getPlatformType())
                .status(OnlineOrderStatus.NEW)
                .customerName(dto.getCustomerName())
                .customerPhone(dto.getCustomerPhone())
                .deliveryAddress(dto.getDeliveryAddress())
                .customerNotes(dto.getCustomerNotes())
                .itemsTotal(dto.getItemsTotal())
                .taxAmount(dto.getTaxAmount())
                .deliveryCharge(dto.getDeliveryCharge())
                .discount(dto.getDiscount())
                .grandTotal(dto.getGrandTotal())
                .paymentMode(dto.getPaymentMode())
                .prepaid(dto.isPrepaid())
                .deliveryPartnerName(dto.getDeliveryPartnerName())
                .deliveryPartnerPhone(dto.getDeliveryPartnerPhone())
                .placedAt(dto.getPlacedAt() != null ? dto.getPlacedAt() : LocalDateTime.now())
                .items(items)
                .build();

        for (IncomingOrderItemDto itemDto : dto.getItems()) {
            OnlineOrderItem item = OnlineOrderItem.builder()
                    .onlineOrder(order)
                    .itemName(itemDto.getItemName())
                    .platformItemId(itemDto.getPlatformItemId())
                    .internalMenuItemId(itemDto.getMatchedInternalMenuItemId())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(itemDto.getUnitPrice())
                    .totalPrice(itemDto.getTotalPrice())
                    .customization(itemDto.getCustomization())
                    .notes(itemDto.getNotes())
                    .build();
            items.add(item);
        }
        return order;
    }

    private void recordHistory(OnlineOrder order, OnlineOrderStatus prev,
                                OnlineOrderStatus next, String notes) {
        statusHistoryRepository.save(OnlineOrderStatusHistory.builder()
                .onlineOrder(order)
                .previousStatus(prev != null ? prev : next)
                .newStatus(next)
                .notes(notes)
                .changedBy("system")
                .build());
    }

    private OnlineOrder fetchOrThrow(Long id) {
        return onlineOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Online order not found: " + id));
    }

    private synchronized String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!datePart.equals(counterDate)) {
            onlineCounter.set(1);
            counterDate = datePart;
        }
        return "ONL-" + datePart + "-" + String.format("%04d", onlineCounter.getAndIncrement());
    }
}
