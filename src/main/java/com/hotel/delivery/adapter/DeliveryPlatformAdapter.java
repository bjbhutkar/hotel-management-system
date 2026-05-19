package com.hotel.delivery.adapter;

import com.hotel.delivery.dto.IncomingOrderDto;
import com.hotel.delivery.dto.MenuSyncItemDto;
import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.entity.PlatformCredential;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;

import java.util.List;

/**
 * Pluggable adapter contract for food delivery platform integrations.
 *
 * Each platform (Zomato, Swiggy, etc.) provides one implementation.
 * The DeliveryIntegrationService discovers all adapters via Spring's
 * collection injection and dispatches to the right one by PlatformType.
 *
 * Adapter implementations must be Spring @Component beans.
 */
public interface DeliveryPlatformAdapter {

    PlatformType getPlatformType();

    /**
     * Verifies that the supplied credentials can reach the platform API.
     * Returns true on success; throws or returns false on failure.
     */
    boolean testConnection(DeliveryPlatform platform, PlatformCredential credential);

    /**
     * Polls the platform API for orders that have arrived since the last poll.
     * Must be idempotent — implementations track which orders they have returned.
     */
    List<IncomingOrderDto> pollNewOrders(DeliveryPlatform platform, PlatformCredential credential);

    /**
     * Accepts a pending order and communicates the preparation time to the platform.
     */
    void acceptOrder(DeliveryPlatform platform, PlatformCredential credential,
                     String platformOrderId, int prepTimeMinutes);

    /**
     * Rejects a pending order with a human-readable reason.
     */
    void rejectOrder(DeliveryPlatform platform, PlatformCredential credential,
                     String platformOrderId, String reason);

    /**
     * Pushes a status transition for an already-accepted order.
     */
    void updateOrderStatus(DeliveryPlatform platform, PlatformCredential credential,
                           String platformOrderId, OnlineOrderStatus status);

    /**
     * Pushes the full restaurant menu to the platform.
     */
    void syncFullMenu(DeliveryPlatform platform, PlatformCredential credential,
                      List<MenuSyncItemDto> items);

    /**
     * Enables, disables, or reprices a single item on the platform.
     */
    void syncMenuItem(DeliveryPlatform platform, PlatformCredential credential,
                      MenuSyncItemDto item);
}
