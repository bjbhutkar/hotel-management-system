package com.hotel.delivery.adapter;

import com.hotel.delivery.dto.IncomingOrderDto;
import com.hotel.delivery.dto.IncomingOrderItemDto;
import com.hotel.delivery.dto.MenuSyncItemDto;
import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.entity.PlatformCredential;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.entity.MenuItem;
import com.hotel.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fully functional mock adapter for end-to-end testing without real API credentials.
 *
 * Behaviour:
 * - Generates a realistic fake order every ~90 seconds when the platform is active.
 * - Picks 1–3 random items from the local menu database.
 * - Simulates accept/reject by logging only (no network call needed).
 *
 * Enable in Platform Configuration screen by activating the "Mock (Testing)" platform.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPlatformAdapter implements DeliveryPlatformAdapter {

    private static final int  MOCK_INTERVAL_SECONDS = 90;
    private static final long SEED = System.currentTimeMillis();

    private final MenuItemRepository menuItemRepository;

    private final AtomicInteger orderCounter   = new AtomicInteger(1000);
    private final Set<String>   returnedOrders = ConcurrentHashMap.newKeySet();
    private LocalDateTime       lastGenerated  = LocalDateTime.now().minusMinutes(2);

    private static final String[] CUSTOMER_NAMES = {
        "Arjun Sharma", "Priya Patel", "Rohit Verma", "Sneha Nair",
        "Vikram Singh", "Ananya Iyer", "Manish Gupta", "Divya Reddy"
    };
    private static final String[] ADDRESSES = {
        "12 MG Road, Koramangala, Bengaluru", "45 Andheri West, Mumbai",
        "78 Salt Lake, Kolkata", "21 Anna Nagar, Chennai",
        "33 Banjara Hills, Hyderabad", "9 Civil Lines, Pune"
    };
    private static final String[] PAYMENT_MODES = { "ONLINE", "ONLINE", "ONLINE", "COD" };
    private static final String[] NOTES = {
        "", "", "Less spicy please", "Extra napkins", "Ring the bell", ""
    };

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.MOCK;
    }

    @Override
    public boolean testConnection(DeliveryPlatform platform, PlatformCredential credential) {
        log.info("[MOCK] Connection test: OK");
        return true;
    }

    @Override
    public List<IncomingOrderDto> pollNewOrders(DeliveryPlatform platform, PlatformCredential credential) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(lastGenerated.plusSeconds(MOCK_INTERVAL_SECONDS))) {
            return List.of();
        }

        List<MenuItem> availableItems = menuItemRepository.findByAvailableTrue();
        if (availableItems.isEmpty()) {
            log.warn("[MOCK] No available menu items — skipping mock order generation");
            return List.of();
        }

        IncomingOrderDto order = generateOrder(availableItems, now);
        if (returnedOrders.contains(order.getPlatformOrderId())) {
            return List.of();
        }

        returnedOrders.add(order.getPlatformOrderId());
        lastGenerated = now;
        log.info("[MOCK] Generated test order {} from {} — ₹{}",
                order.getPlatformOrderId(), order.getCustomerName(), order.getGrandTotal());
        return List.of(order);
    }

    @Override
    public void acceptOrder(DeliveryPlatform platform, PlatformCredential credential,
                            String platformOrderId, int prepTimeMinutes) {
        log.info("[MOCK] Order {} ACCEPTED — prep time {}min", platformOrderId, prepTimeMinutes);
    }

    @Override
    public void rejectOrder(DeliveryPlatform platform, PlatformCredential credential,
                            String platformOrderId, String reason) {
        log.info("[MOCK] Order {} REJECTED — reason: {}", platformOrderId, reason);
    }

    @Override
    public void updateOrderStatus(DeliveryPlatform platform, PlatformCredential credential,
                                  String platformOrderId, OnlineOrderStatus status) {
        log.info("[MOCK] Order {} status -> {}", platformOrderId, status);
    }

    @Override
    public void syncFullMenu(DeliveryPlatform platform, PlatformCredential credential,
                             List<MenuSyncItemDto> items) {
        log.info("[MOCK] Full menu sync received — {} items", items.size());
    }

    @Override
    public void syncMenuItem(DeliveryPlatform platform, PlatformCredential credential,
                             MenuSyncItemDto item) {
        log.info("[MOCK] Sync item '{}' price=₹{} available={}",
                item.getName(), item.getPrice(), item.isAvailable());
    }

    // ── Order generation ─────────────────────────────────────────────────────

    private IncomingOrderDto generateOrder(List<MenuItem> menu, LocalDateTime now) {
        Random rng = new Random(SEED + now.toEpochSecond(java.time.ZoneOffset.UTC));

        String orderId = "MOCK-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                         + "-" + orderCounter.getAndIncrement();

        String customer = CUSTOMER_NAMES[rng.nextInt(CUSTOMER_NAMES.length)];
        String address  = ADDRESSES[rng.nextInt(ADDRESSES.length)];
        String payment  = PAYMENT_MODES[rng.nextInt(PAYMENT_MODES.length)];
        String note     = NOTES[rng.nextInt(NOTES.length)];

        int itemCount = 1 + rng.nextInt(Math.min(3, menu.size()));
        List<MenuItem> selected = new ArrayList<>(menu);
        Collections.shuffle(selected, rng);
        selected = selected.subList(0, itemCount);

        List<IncomingOrderItemDto> items = new ArrayList<>();
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (MenuItem mi : selected) {
            int qty = 1 + rng.nextInt(3);
            BigDecimal lineTotal = mi.getPrice().multiply(BigDecimal.valueOf(qty));
            items.add(IncomingOrderItemDto.builder()
                    .platformItemId("MOCK-ITEM-" + mi.getId())
                    .itemName(mi.getName())
                    .quantity(qty)
                    .unitPrice(mi.getPrice())
                    .totalPrice(lineTotal)
                    .matchedInternalMenuItemId(mi.getId())
                    .build());
            itemsTotal = itemsTotal.add(lineTotal);
        }

        BigDecimal tax      = itemsTotal.multiply(new BigDecimal("0.18"))
                                        .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal delivery = new BigDecimal("49.00");
        BigDecimal total    = itemsTotal.add(tax).add(delivery);

        return IncomingOrderDto.builder()
                .platformOrderId(orderId)
                .platformType(PlatformType.MOCK)
                .customerName(customer)
                .customerPhone("98" + (10000000 + rng.nextInt(89999999)))
                .deliveryAddress(address)
                .customerNotes(note)
                .items(items)
                .itemsTotal(itemsTotal)
                .taxAmount(tax)
                .deliveryCharge(delivery)
                .discount(BigDecimal.ZERO)
                .grandTotal(total)
                .paymentMode(payment)
                .prepaid(!"COD".equals(payment))
                .placedAt(now)
                .rawPayload("{\"mock\":true,\"order_id\":\"" + orderId + "\"}")
                .build();
    }
}
