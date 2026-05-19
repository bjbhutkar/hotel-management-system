package com.hotel.ui;

import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.entity.PlatformCredential;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.repository.PlatformCredentialRepository;
import com.hotel.delivery.service.CredentialService;
import com.hotel.delivery.service.DeliveryIntegrationService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformConfigController {

    private final DeliveryIntegrationService  deliveryIntegrationService;
    private final PlatformCredentialRepository credentialRepository;
    private final CredentialService           credentialService;
    private final StageManager                stageManager;

    // ── Zomato tab ────────────────────────────────────────────────────────────
    @FXML private CheckBox       zomatoActiveCheck;
    @FXML private TextField      zomatoRestaurantId;
    @FXML private PasswordField  zomatoApiKey;
    @FXML private PasswordField  zomatoApiSecret;
    @FXML private Label          zomatoStatusLabel;
    @FXML private Label          zomatoWebhookUrl;

    // ── Swiggy tab ────────────────────────────────────────────────────────────
    @FXML private CheckBox       swiggyActiveCheck;
    @FXML private TextField      swiggyRestaurantId;
    @FXML private PasswordField  swiggyApiKey;
    @FXML private PasswordField  swiggyApiSecret;
    @FXML private Label          swiggyStatusLabel;
    @FXML private Label          swiggyWebhookUrl;

    // ── Mock tab ──────────────────────────────────────────────────────────────
    @FXML private CheckBox  mockActiveCheck;
    @FXML private Label     mockStatusLabel;
    @FXML private Label     mockInfoLabel;

    @FXML
    public void initialize() {
        loadPlatformState(PlatformType.ZOMATO, zomatoActiveCheck, zomatoRestaurantId,
                zomatoApiKey, zomatoApiSecret, zomatoStatusLabel, zomatoWebhookUrl);
        loadPlatformState(PlatformType.SWIGGY, swiggyActiveCheck, swiggyRestaurantId,
                swiggyApiKey, swiggyApiSecret, swiggyStatusLabel, swiggyWebhookUrl);
        loadMockState();
    }

    // ── Zomato actions ────────────────────────────────────────────────────────

    @FXML public void handleSaveZomato() {
        savePlatform(PlatformType.ZOMATO,
                zomatoActiveCheck.isSelected(),
                zomatoRestaurantId.getText(),
                zomatoApiKey.getText(),
                zomatoApiSecret.getText(),
                zomatoStatusLabel);
    }

    @FXML public void handleTestZomato() {
        testConnection(PlatformType.ZOMATO, zomatoStatusLabel);
    }

    // ── Swiggy actions ────────────────────────────────────────────────────────

    @FXML public void handleSaveSwiggy() {
        savePlatform(PlatformType.SWIGGY,
                swiggyActiveCheck.isSelected(),
                swiggyRestaurantId.getText(),
                swiggyApiKey.getText(),
                swiggyApiSecret.getText(),
                swiggyStatusLabel);
    }

    @FXML public void handleTestSwiggy() {
        testConnection(PlatformType.SWIGGY, swiggyStatusLabel);
    }

    // ── Mock actions ──────────────────────────────────────────────────────────

    @FXML public void handleSaveMock() {
        DeliveryPlatform platform = getPlatform(PlatformType.MOCK);
        if (platform == null) return;
        platform.setActive(mockActiveCheck.isSelected());
        deliveryIntegrationService.savePlatform(platform);
        setStatus(mockStatusLabel,
                mockActiveCheck.isSelected()
                        ? "Mock enabled — fake orders will arrive every ~90 seconds"
                        : "Mock disabled",
                mockActiveCheck.isSelected());
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML public void handleBack() throws IOException {
        stageManager.showOnlineOrdersDashboard();
    }

    // ── Shared logic ──────────────────────────────────────────────────────────

    private void savePlatform(PlatformType type, boolean active, String restaurantId,
                               String apiKey, String apiSecret, Label statusLabel) {
        DeliveryPlatform platform = getPlatform(type);
        if (platform == null) return;

        platform.setActive(active);
        platform.setRestaurantId(restaurantId);
        deliveryIntegrationService.savePlatform(platform);

        PlatformCredential cred = credentialRepository.findByPlatformId(platform.getId())
                .orElseGet(() -> {
                    PlatformCredential c = new PlatformCredential();
                    c.setPlatform(platform);
                    return c;
                });

        if (apiKey    != null && !apiKey.isBlank())
            cred.setEncryptedApiKey(credentialService.encrypt(apiKey));
        if (apiSecret != null && !apiSecret.isBlank())
            cred.setEncryptedApiSecret(credentialService.encrypt(apiSecret));

        credentialRepository.save(cred);
        setStatus(statusLabel, "Saved successfully", true);
        log.info("Platform {} configuration saved (active={})", type, active);
    }

    private void testConnection(PlatformType type, Label statusLabel) {
        DeliveryPlatform platform = getPlatform(type);
        if (platform == null) return;
        boolean ok = deliveryIntegrationService.testConnection(platform);
        setStatus(statusLabel, ok ? "Connection successful" : "Connection failed — check credentials", ok);
    }

    private void loadPlatformState(PlatformType type, CheckBox activeCheck,
                                    TextField restaurantId, PasswordField apiKey,
                                    PasswordField apiSecret, Label statusLabel,
                                    Label webhookUrl) {
        DeliveryPlatform platform = getPlatform(type);
        if (platform == null) return;
        activeCheck.setSelected(platform.isActive());
        restaurantId.setText(nullSafe(platform.getRestaurantId()));
        if (webhookUrl != null)
            webhookUrl.setText("http://<YOUR-PUBLIC-IP>:9090/webhook/" + type.name().toLowerCase());
        setStatus(statusLabel, platform.isActive() ? "Active" : "Inactive", platform.isActive());
    }

    private void loadMockState() {
        DeliveryPlatform platform = getPlatform(PlatformType.MOCK);
        if (platform == null) return;
        mockActiveCheck.setSelected(platform.isActive());
        if (mockInfoLabel != null)
            mockInfoLabel.setText("Generates a realistic fake order every ~90 seconds using your menu items. No network connection required.");
        setStatus(mockStatusLabel, platform.isActive() ? "Active — receiving test orders" : "Inactive", platform.isActive());
    }

    private DeliveryPlatform getPlatform(PlatformType type) {
        return deliveryIntegrationService.getAllPlatforms().stream()
                .filter(p -> p.getPlatformType() == type)
                .findFirst().orElse(null);
    }

    private void setStatus(Label label, String text, boolean success) {
        if (label == null) return;
        label.setText(text);
        label.setStyle("-fx-text-fill: " + (success ? "#27ae60" : "#e74c3c") + "; -fx-font-weight: bold;");
    }

    private String nullSafe(String s) { return s != null ? s : ""; }
}
