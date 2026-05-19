package com.hotel.delivery.service;

import com.hotel.delivery.adapter.DeliveryPlatformAdapter;
import com.hotel.delivery.dto.IncomingOrderDto;
import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.entity.PlatformCredential;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.repository.DeliveryPlatformRepository;
import com.hotel.delivery.repository.PlatformCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central orchestrator for all delivery platform interactions.
 *
 * Responsibilities:
 * 1. Discover all registered and active platforms on startup.
 * 2. Poll each platform for new orders on a fixed schedule.
 * 3. Route accept/reject/status-update calls to the correct platform adapter.
 * 4. Expose simple API for the JavaFX UI controllers.
 */
@Slf4j
@Service
public class DeliveryIntegrationService {

    private final Map<PlatformType, DeliveryPlatformAdapter> adapterMap;
    private final DeliveryPlatformRepository                  platformRepository;
    private final PlatformCredentialRepository                credentialRepository;
    private final OnlineOrderProcessingService                orderProcessingService;

    public DeliveryIntegrationService(List<DeliveryPlatformAdapter>  adapters,
                                      DeliveryPlatformRepository      platformRepository,
                                      PlatformCredentialRepository    credentialRepository,
                                      OnlineOrderProcessingService    orderProcessingService) {
        this.adapterMap            = adapters.stream()
                .collect(Collectors.toMap(DeliveryPlatformAdapter::getPlatformType, a -> a));
        this.platformRepository    = platformRepository;
        this.credentialRepository  = credentialRepository;
        this.orderProcessingService = orderProcessingService;
        log.info("DeliveryIntegrationService ready — registered adapters: {}", this.adapterMap.keySet());
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${delivery.poll.interval-ms:30000}")
    public void pollAllPlatforms() {
        List<DeliveryPlatform> active = platformRepository.findByActiveTrue();
        if (active.isEmpty()) return;

        for (DeliveryPlatform platform : active) {
            try {
                pollPlatform(platform);
            } catch (Exception e) {
                log.error("Error polling platform {}: {}", platform.getDisplayName(), e.getMessage());
            }
        }
    }

    private void pollPlatform(DeliveryPlatform platform) {
        DeliveryPlatformAdapter adapter = adapterMap.get(platform.getPlatformType());
        if (adapter == null) {
            log.warn("No adapter found for platform type: {}", platform.getPlatformType());
            return;
        }

        Optional<PlatformCredential> credOpt = credentialRepository.findByPlatformId(platform.getId());
        PlatformCredential credential = credOpt.orElse(null);

        List<IncomingOrderDto> newOrders = adapter.pollNewOrders(platform, credential);
        platform.setLastPolledAt(LocalDateTime.now());
        platformRepository.save(platform);

        for (IncomingOrderDto dto : newOrders) {
            try {
                orderProcessingService.processIncomingOrder(dto);
            } catch (Exception e) {
                log.error("Failed to process incoming order {} from {}: {}",
                        dto.getPlatformOrderId(), dto.getPlatformType(), e.getMessage());
            }
        }
    }

    // ── Order actions (called from UI) ────────────────────────────────────────

    @Transactional
    public void acceptOrder(Long onlineOrderId, int prepTimeMinutes) {
        var order = orderProcessingService.acceptOrder(onlineOrderId, prepTimeMinutes);
        callAdapterSafely(order.getPlatformType(), order.getPlatformOrderId(),
                (adapter, platform, cred) ->
                        adapter.acceptOrder(platform, cred, order.getPlatformOrderId(), prepTimeMinutes));
    }

    @Transactional
    public void rejectOrder(Long onlineOrderId, String reason) {
        var order = orderProcessingService.rejectOrder(onlineOrderId, reason);
        callAdapterSafely(order.getPlatformType(), order.getPlatformOrderId(),
                (adapter, platform, cred) ->
                        adapter.rejectOrder(platform, cred, order.getPlatformOrderId(), reason));
    }

    @Transactional
    public void updateStatus(Long onlineOrderId, OnlineOrderStatus newStatus) {
        var order = orderProcessingService.updateStatus(onlineOrderId, newStatus);
        callAdapterSafely(order.getPlatformType(), order.getPlatformOrderId(),
                (adapter, platform, cred) ->
                        adapter.updateOrderStatus(platform, cred, order.getPlatformOrderId(), newStatus));
    }

    // ── Platform management ───────────────────────────────────────────────────

    public List<DeliveryPlatform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    public DeliveryPlatform savePlatform(DeliveryPlatform platform) {
        return platformRepository.save(platform);
    }

    public boolean testConnection(DeliveryPlatform platform) {
        DeliveryPlatformAdapter adapter = adapterMap.get(platform.getPlatformType());
        if (adapter == null) return false;
        Optional<PlatformCredential> credOpt = credentialRepository.findByPlatformId(platform.getId());
        return credOpt.isPresent() && adapter.testConnection(platform, credOpt.get());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface AdapterCall {
        void execute(DeliveryPlatformAdapter adapter, DeliveryPlatform platform, PlatformCredential cred);
    }

    private void callAdapterSafely(PlatformType type, String orderId, AdapterCall call) {
        DeliveryPlatformAdapter adapter = adapterMap.get(type);
        if (adapter == null) { log.warn("No adapter for {}", type); return; }
        platformRepository.findByPlatformType(type).ifPresent(platform -> {
            PlatformCredential cred = credentialRepository.findByPlatformId(platform.getId()).orElse(null);
            try {
                call.execute(adapter, platform, cred);
            } catch (Exception e) {
                log.error("Adapter call failed for order {} on {}: {}", orderId, type, e.getMessage());
            }
        });
    }
}
