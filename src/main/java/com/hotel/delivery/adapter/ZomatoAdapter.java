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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zomato for Business restaurant-side integration.
 *
 * SETUP REQUIRED:
 *   1. Sign up at https://www.zomato.com/business
 *   2. Obtain API credentials from the Zomato Partner Portal
 *   3. Enter Restaurant ID, API Key and API Secret in Platform Configuration screen
 *
 * API BASE: https://partners.zomato.com/api/v3
 * Auth: Bearer token obtained via OAuth2 client_credentials flow
 *
 * NOTE: Zomato's restaurant partner API is available only after a formal
 * integration agreement. These stubs document the expected contract; replace
 * the TODO sections with real endpoint paths once credentials are provisioned.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZomatoAdapter implements DeliveryPlatformAdapter {

    private static final String API_BASE = "https://partners.zomato.com/api/v3";

    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Tracks last-seen order IDs so each order is returned only once per session
    private final Map<String, LocalDateTime> seenOrderIds = new ConcurrentHashMap<>();

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.ZOMATO;
    }

    @Override
    public boolean testConnection(DeliveryPlatform platform, PlatformCredential credential) {
        try {
            String token = obtainAccessToken(platform, credential);
            return token != null && !token.isBlank();
        } catch (Exception e) {
            log.warn("Zomato connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<IncomingOrderDto> pollNewOrders(DeliveryPlatform platform, PlatformCredential credential) {
        // TODO: Replace with actual Zomato partner API endpoint
        // Expected endpoint: GET /restaurants/{restaurantId}/orders?status=placed&limit=50
        // Returns JSON array of order objects
        //
        // try {
        //     String token = obtainAccessToken(platform, credential);
        //     HttpRequest request = HttpRequest.newBuilder()
        //         .uri(URI.create(API_BASE + "/restaurants/" + platform.getRestaurantId()
        //                        + "/orders?status=placed"))
        //         .header("Authorization", "Bearer " + token)
        //         .header("Accept", "application/json")
        //         .GET().build();
        //     HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        //     return parseOrdersResponse(response.body(), platform);
        // } catch (Exception e) {
        //     log.error("Failed to poll Zomato orders: {}", e.getMessage());
        //     throw new RuntimeException("Zomato poll failed", e);
        // }
        log.debug("Zomato polling — partner API credentials not yet configured");
        return List.of();
    }

    @Override
    public void acceptOrder(DeliveryPlatform platform, PlatformCredential credential,
                            String platformOrderId, int prepTimeMinutes) {
        // TODO: POST /restaurants/{restaurantId}/orders/{orderId}/accept
        // Body: { "prep_time": prepTimeMinutes }
        log.info("[ZOMATO STUB] Accept order {} prep={}min", platformOrderId, prepTimeMinutes);
    }

    @Override
    public void rejectOrder(DeliveryPlatform platform, PlatformCredential credential,
                            String platformOrderId, String reason) {
        // TODO: POST /restaurants/{restaurantId}/orders/{orderId}/reject
        // Body: { "reason": reason }
        log.info("[ZOMATO STUB] Reject order {} reason={}", platformOrderId, reason);
    }

    @Override
    public void updateOrderStatus(DeliveryPlatform platform, PlatformCredential credential,
                                  String platformOrderId, OnlineOrderStatus status) {
        // TODO: POST /restaurants/{restaurantId}/orders/{orderId}/status
        // Body: { "status": mapStatus(status) }
        log.info("[ZOMATO STUB] Status update order {} -> {}", platformOrderId, status);
    }

    @Override
    public void syncFullMenu(DeliveryPlatform platform, PlatformCredential credential,
                             List<MenuSyncItemDto> items) {
        // TODO: PUT /restaurants/{restaurantId}/menu
        // Body: JSON representation of full menu
        log.info("[ZOMATO STUB] Full menu sync — {} items", items.size());
    }

    @Override
    public void syncMenuItem(DeliveryPlatform platform, PlatformCredential credential,
                             MenuSyncItemDto item) {
        // TODO: PATCH /restaurants/{restaurantId}/menu/items/{platformItemId}
        // Body: { "price": item.getPrice(), "available": item.isAvailable() }
        log.info("[ZOMATO STUB] Sync item '{}' available={}", item.getName(), item.isAvailable());
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private String obtainAccessToken(DeliveryPlatform platform, PlatformCredential credential) throws Exception {
        // TODO: POST https://partners.zomato.com/oauth/token
        // Form: grant_type=client_credentials&client_id=apiKey&client_secret=apiSecret
        String apiKey    = credentialService.decrypt(credential.getEncryptedApiKey());
        String apiSecret = credentialService.decrypt(credential.getEncryptedApiSecret());
        log.debug("Would obtain Zomato token for restaurant {}", platform.getRestaurantId());
        return "stub-token";
    }

    private List<IncomingOrderDto> parseOrdersResponse(String json, DeliveryPlatform platform) throws Exception {
        List<IncomingOrderDto> result = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode orders = root.path("orders");
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
        for (JsonNode itemNode : node.path("items")) {
            items.add(IncomingOrderItemDto.builder()
                    .platformItemId(itemNode.path("item_id").asText())
                    .itemName(itemNode.path("name").asText())
                    .quantity(itemNode.path("quantity").asInt(1))
                    .unitPrice(new BigDecimal(itemNode.path("unit_price").asText("0")))
                    .totalPrice(new BigDecimal(itemNode.path("total_price").asText("0")))
                    .customization(itemNode.path("customization").asText(""))
                    .build());
        }
        return IncomingOrderDto.builder()
                .platformOrderId(node.path("order_id").asText())
                .platformType(PlatformType.ZOMATO)
                .customerName(node.path("customer").path("name").asText())
                .customerPhone(node.path("customer").path("phone").asText())
                .deliveryAddress(node.path("delivery_address").path("formatted").asText())
                .customerNotes(node.path("special_instructions").asText())
                .items(items)
                .itemsTotal(new BigDecimal(node.path("subtotal").asText("0")))
                .taxAmount(new BigDecimal(node.path("taxes").asText("0")))
                .deliveryCharge(new BigDecimal(node.path("delivery_charge").asText("0")))
                .discount(new BigDecimal(node.path("discount").asText("0")))
                .grandTotal(new BigDecimal(node.path("grand_total").asText("0")))
                .paymentMode(node.path("payment_method").asText("ONLINE"))
                .prepaid("ONLINE".equalsIgnoreCase(node.path("payment_method").asText()))
                .placedAt(LocalDateTime.now())
                .rawPayload(node.toString())
                .build();
    }
}
