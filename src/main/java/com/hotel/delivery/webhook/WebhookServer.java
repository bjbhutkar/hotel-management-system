package com.hotel.delivery.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.delivery.entity.WebhookLog;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.repository.WebhookLogRepository;
import com.hotel.delivery.service.OnlineOrderProcessingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;

/**
 * Lightweight embedded HTTP server that receives delivery platform webhooks.
 *
 * Listens on delivery.webhook.port (default 9090).
 * Set delivery.webhook.enabled=false to disable (polling-only mode).
 *
 * Endpoint: POST http://<your-ip>:<port>/webhook/<platform>
 * Supported platform path segments: zomato, swiggy
 *
 * Exposes the public URL to enter in each platform's Partner Portal:
 *   http://<PUBLIC_IP>:9090/webhook/zomato
 *   http://<PUBLIC_IP>:9090/webhook/swiggy
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookServer {

    @Value("${delivery.webhook.enabled:false}")
    private boolean enabled;

    @Value("${delivery.webhook.port:9090}")
    private int port;

    private final ObjectMapper                  objectMapper;
    private final WebhookLogRepository          webhookLogRepository;
    private final OnlineOrderProcessingService  orderProcessingService;

    private HttpServer server;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("Webhook server disabled — using polling mode");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/webhook/zomato", ex -> handleWebhook(ex, PlatformType.ZOMATO));
            server.createContext("/webhook/swiggy", ex -> handleWebhook(ex, PlatformType.SWIGGY));
            server.createContext("/health",         ex -> { try { writeResponse(ex, 200, "{\"status\":\"UP\"}"); } catch (Exception ignored) {} });
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            log.info("Webhook server started on port {} — POST /webhook/{{zomato|swiggy}}", port);
        } catch (Exception e) {
            log.error("Failed to start webhook server on port {}: {}", port, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(2);
            log.info("Webhook server stopped");
        }
    }

    private void handleWebhook(HttpExchange exchange, PlatformType platformType) {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            byte[] body    = exchange.getRequestBody().readAllBytes();
            String payload = new String(body, StandardCharsets.UTF_8);
            String hash    = sha256Hex(body);

            // Replay protection — ignore duplicate payloads
            if (webhookLogRepository.existsByPayloadHash(hash)) {
                log.debug("Duplicate webhook payload — hash {} already processed", hash);
                writeResponse(exchange, 200, "{\"status\":\"duplicate\"}");
                return;
            }

            WebhookLog webhookLog = WebhookLog.builder()
                    .platformType(platformType)
                    .rawPayload(payload)
                    .payloadHash(hash)
                    .processed(false)
                    .build();
            webhookLog = webhookLogRepository.save(webhookLog);

            try {
                processWebhookPayload(platformType, payload, webhookLog);
                webhookLog.setProcessed(true);
                webhookLogRepository.save(webhookLog);
                writeResponse(exchange, 200, "{\"status\":\"ok\"}");
            } catch (Exception e) {
                webhookLog.setProcessingError(e.getMessage());
                webhookLogRepository.save(webhookLog);
                log.error("Webhook processing error for {}: {}", platformType, e.getMessage());
                writeResponse(exchange, 500, "{\"error\":\"processing failed\"}");
            }
        } catch (Exception e) {
            log.error("Webhook handler error: {}", e.getMessage());
            try { writeResponse(exchange, 500, "{\"error\":\"internal error\"}"); } catch (Exception ignored) {}
        }
    }

    private void processWebhookPayload(PlatformType platformType, String payload,
                                        WebhookLog webhookLog) throws Exception {
        JsonNode root = objectMapper.readTree(payload);

        // Both Zomato and Swiggy send an event_type field in webhook payloads
        String eventType = root.path("event_type").asText(
                root.path("type").asText("order.new"));
        webhookLog.setEventType(eventType);

        String platformOrderId = root.path("order_id").asText(
                root.path("data").path("order_id").asText(""));
        webhookLog.setPlatformOrderId(platformOrderId);

        // TODO: Parse the full order payload and call orderProcessingService.processIncomingOrder()
        // The adapter's buildOrderDto() helper should be extracted and reused here.
        log.info("Webhook received — platform={} event={} orderId={}", platformType, eventType, platformOrderId);
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            return String.valueOf(data.hashCode());
        }
    }
}
