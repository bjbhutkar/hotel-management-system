package com.hotel.delivery.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.delivery.dto.IncomingOrderDto;
import com.hotel.delivery.dto.IncomingOrderItemDto;
import com.hotel.delivery.dto.MenuSyncItemDto;
import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.entity.PlatformCredential;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.service.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Swiggy for Restaurant (SFR) integration.
 *
 * SETUP REQUIRED:
 *   1. Register at https://www.swiggy.com/restaurant-partner
 *   2. Obtain credentials from Swiggy Restaurant Partner Portal
 *   3. Enter Restaurant ID and API Key in Platform Configuration screen
 *
 * API BASE: https://partner.swiggy.com/api/v2
 * Auth: HMAC-SHA256 signed requests using apiKey + apiSecret
 *
 * NOTE: Swiggy's partner API requires a signed partnership agreement.
 * These stubs document the expected contract; replace TODO sections with
 * real endpoint paths once credentials are provisioned.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SwiggyAdapter implements DeliveryPlatformAdapter {

    private static final String API_BASE = "https://partner.swiggy.com/api/v2";

    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, LocalDateTime> seenOrderIds = new ConcurrentHashMap<>();

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SWIGGY;
    }

    @Override
    public boolean testConnection(DeliveryPlatform platform, PlatformCredential credential) {
        // TODO: GET /restaurants/{restaurantId}/info
        // Verify HMAC signature accepted
        log.debug("Swiggy connection test — partner API credentials not yet configured");
        return false;
    }

    @Override
    public List<IncomingOrderDto> pollNewOrders(DeliveryPlatform platform, PlatformCredential credential) {
        // TODO: GET /restaurants/{restaurantId}/orders/pending
        // Swiggy uses HMAC-signed requests:
        //   1. Build canonical request string: METHOD + \n + PATH + \n + TIMESTAMP
        //   2. Sign with HMAC-SHA256 using apiSecret
        //   3. Header: X-Swiggy-Signature: {apiKey}:{timestamp}:{signature}
        log.debug("Swiggy polling — partner API credentials not yet configured");
        return List.of();
    }

    @Override
    public void acceptOrder(DeliveryPlatform platform, PlatformCredential credential,
                            String platformOrderId, int prepTimeMinutes) {
        // TODO: POST /restaurants/{restaurantId}/orders/{orderId}/accept
        // Body: { "eta": prepTimeMinutes }
        log.info("[SWIGGY STUB] Accept order {} prep={}min", platformOrderId, prepTimeMinutes);
    }

    @Override
    public void rejectOrder(DeliveryPlatform platform, PlatformCredential credential,
                            String platformOrderId, String reason) {
        // TODO: POST /restaurants/{restaurantId}/orders/{orderId}/reject
        // Body: { "cancellation_reason": reason, "reason_code": mapReasonCode(reason) }
        log.info("[SWIGGY STUB] Reject order {} reason={}", platformOrderId, reason);
    }

    @Override
    public void updateOrderStatus(DeliveryPlatform platform, PlatformCredential credential,
                                  String platformOrderId, OnlineOrderStatus status) {
        // TODO: POST /restaurants/{restaurantId}/orders/{orderId}/status
        // Body: { "order_status": mapToSwiggyStatus(status) }
        log.info("[SWIGGY STUB] Status update order {} -> {}", platformOrderId, status);
    }

    @Override
    public void syncFullMenu(DeliveryPlatform platform, PlatformCredential credential,
                             List<MenuSyncItemDto> items) {
        // TODO: PUT /restaurants/{restaurantId}/catalogue
        log.info("[SWIGGY STUB] Full menu sync — {} items", items.size());
    }

    @Override
    public void syncMenuItem(DeliveryPlatform platform, PlatformCredential credential,
                             MenuSyncItemDto item) {
        // TODO: PATCH /restaurants/{restaurantId}/catalogue/items/{platformItemId}
        log.info("[SWIGGY STUB] Sync item '{}' available={}", item.getName(), item.isAvailable());
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private List<IncomingOrderDto> parseOrdersResponse(String json) throws Exception {
        List<IncomingOrderDto> result = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode orders = root.path("data").path("orders");
        for (JsonNode node : orders) {
            String orderId = node.path("order_id").asText();
            if (seenOrderIds.containsKey(orderId)) continue;
            seenOrderIds.put(orderId, LocalDateTime.now());
            result.add(buildOrderDto(node));
        }
        return result;
    }

    private IncomingOrderDto buildOrderDto(JsonNode node) {
        List<IncomingOrderItemDto> items = new ArrayList<>();
        for (JsonNode itemNode : node.path("order_items")) {
            items.add(IncomingOrderItemDto.builder()
                    .platformItemId(itemNode.path("item_id").asText())
                    .itemName(itemNode.path("name").asText())
                    .quantity(itemNode.path("units").asInt(1))
                    .unitPrice(new BigDecimal(itemNode.path("base_price").asText("0")))
                    .totalPrice(new BigDecimal(itemNode.path("total_price").asText("0")))
                    .customization(itemNode.path("addons").toString())
                    .build());
        }
        return IncomingOrderDto.builder()
                .platformOrderId(node.path("order_id").asText())
                .platformType(PlatformType.SWIGGY)
                .customerName(node.path("delivery_address").path("name").asText())
                .customerPhone(node.path("delivery_address").path("mobile").asText())
                .deliveryAddress(node.path("delivery_address").path("address").asText())
                .customerNotes(node.path("customer_note").asText())
                .items(items)
                .itemsTotal(new BigDecimal(node.path("order_subtotal").asText("0")))
                .taxAmount(new BigDecimal(node.path("vat").asText("0")))
                .deliveryCharge(new BigDecimal(node.path("delivery_charge").asText("0")))
                .discount(new BigDecimal(node.path("discount_share_restaurant").asText("0")))
                .grandTotal(new BigDecimal(node.path("order_total").asText("0")))
                .paymentMode(node.path("payment_method").asText("ONLINE"))
                .prepaid(true)
                .placedAt(LocalDateTime.now())
                .rawPayload(node.toString())
                .build();
    }
}
