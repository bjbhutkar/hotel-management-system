package com.hotel.ui;

import com.hotel.entity.RestaurantConfig;
import com.hotel.service.BillingService;
import com.hotel.service.PrintService;
import com.hotel.service.RestaurantConfigService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsController {

    private final RestaurantConfigService configService;
    private final BillingService          billingService;
    private final PrintService            printService;
    private final StageManager            stageManager;

    // ── Business Info ─────────────────────────────────────────────────────────
    @FXML private TextField  restaurantNameField;
    @FXML private TextArea   addressArea;
    @FXML private TextField  phoneField;
    @FXML private TextField  emailField;
    @FXML private TextField  gstField;
    @FXML private TextField  fssaiField;
    @FXML private TextField  websiteField;

    // ── Tax & Invoice ─────────────────────────────────────────────────────────
    @FXML private TextField  invoicePrefixField;
    @FXML private TextField  currencySymbolField;
    @FXML private TextField  defaultTaxField;

    // ── Branding ──────────────────────────────────────────────────────────────
    @FXML private ImageView  logoPreview;
    @FXML private Label      logoPathLabel;
    @FXML private Label      printerNameLabel;
    @FXML private TextField  footerMessageField;
    @FXML private TextField  thankYouField;

    // ── Preview ───────────────────────────────────────────────────────────────
    @FXML private TextArea   invoicePreviewArea;

    // ── Status ────────────────────────────────────────────────────────────────
    @FXML private Label      statusLabel;

    private String pendingLogoPath; // holds the new logo path before Save

    @FXML
    public void initialize() {
        loadConfig();
        refreshPrinterLabel();
    }

    @FXML
    public void handleChangePrinter() {
        Window window = logoPreview.getScene() != null ? logoPreview.getScene().getWindow() : null;
        printService.changePrinter(window);
        refreshPrinterLabel();
    }

    private void refreshPrinterLabel() {
        if (printerNameLabel == null) return;
        String name = printService.getRememberedPrinterName();
        printerNameLabel.setText(name != null ? name : "Not set — will be prompted on first print");
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    public void handleChooseLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Restaurant Logo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        Window window = logoPreview.getScene().getWindow();
        File selected = chooser.showOpenDialog(window);
        if (selected != null) {
            try {
                pendingLogoPath = configService.copyLogoToStorage(selected);
                loadLogoPreview(pendingLogoPath);
                logoPathLabel.setText(selected.getName());
                showStatus("Logo selected. Click Save to apply.", true);
            } catch (IOException e) {
                showStatus("Could not load logo: " + e.getMessage(), false);
            }
        }
    }

    @FXML
    public void handleRemoveLogo() {
        configService.removeLogo();
        pendingLogoPath = null;
        logoPreview.setImage(null);
        logoPathLabel.setText("No logo selected");
        showStatus("Logo removed.", true);
    }

    @FXML
    public void handleSave() {
        if (!validateFields()) return;

        RestaurantConfig cfg = configService.getConfig();
        cfg.setRestaurantName(restaurantNameField.getText().trim());
        cfg.setAddress(addressArea.getText().trim());
        cfg.setPhoneNumber(phoneField.getText().trim());
        cfg.setEmail(emailField.getText().trim());
        cfg.setGstNumber(gstField.getText().trim());
        cfg.setFssaiNumber(fssaiField.getText().trim());
        cfg.setWebsiteUrl(websiteField.getText().trim());
        cfg.setInvoicePrefix(invoicePrefixField.getText().trim());
        cfg.setCurrencySymbol(currencySymbolField.getText().trim());
        cfg.setFooterMessage(footerMessageField.getText().trim());
        cfg.setThankYouMessage(thankYouField.getText().trim());

        String taxText = defaultTaxField.getText().trim();
        try {
            cfg.setDefaultTaxPercent(new BigDecimal(taxText));
        } catch (NumberFormatException e) {
            showStatus("Default Tax % must be a number.", false);
            return;
        }

        if (pendingLogoPath != null) {
            cfg.setLogoPath(pendingLogoPath);
        }

        configService.saveConfig(cfg);
        showStatus("Settings saved successfully!", true);
        updatePreview();
        log.info("Restaurant configuration saved via Settings screen");
    }

    @FXML
    public void handlePreviewInvoice() {
        updatePreview();
    }

    @FXML
    public void handleBack() throws IOException {
        stageManager.showDashboard();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void loadConfig() {
        RestaurantConfig cfg = configService.getConfig();
        restaurantNameField.setText(safe(cfg.getRestaurantName()));
        addressArea        .setText(safe(cfg.getAddress()));
        phoneField         .setText(safe(cfg.getPhoneNumber()));
        emailField         .setText(safe(cfg.getEmail()));
        gstField           .setText(safe(cfg.getGstNumber()));
        fssaiField         .setText(safe(cfg.getFssaiNumber()));
        websiteField       .setText(safe(cfg.getWebsiteUrl()));
        invoicePrefixField .setText(safe(cfg.getInvoicePrefix()));
        currencySymbolField.setText(safe(cfg.getCurrencySymbol()));
        defaultTaxField    .setText(cfg.getDefaultTaxPercent() != null
                ? cfg.getDefaultTaxPercent().toPlainString() : "18");
        footerMessageField .setText(safe(cfg.getFooterMessage()));
        thankYouField      .setText(safe(cfg.getThankYouMessage()));

        if (cfg.getLogoPath() != null && !cfg.getLogoPath().isBlank()) {
            loadLogoPreview(cfg.getLogoPath());
            logoPathLabel.setText(new File(cfg.getLogoPath()).getName());
        } else {
            logoPathLabel.setText("No logo selected");
        }

        updatePreview();
    }

    private void loadLogoPreview(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            logoPreview.setImage(new Image(fis));
        } catch (Exception e) {
            log.warn("Could not load logo preview from {}: {}", path, e.getMessage());
        }
    }

    private void updatePreview() {
        // Show a sample invoice preview using the current (saved) config
        // In a real order, items would be populated — here we build a minimal placeholder
        invoicePreviewArea.setText(buildSampleInvoiceText());
    }

    private String buildSampleInvoiceText() {
        RestaurantConfig cfg = configService.getConfig();
        String eq  = "=".repeat(48);
        String sep = "-".repeat(48);
        StringBuilder sb = new StringBuilder();
        sb.append(eq).append("\n");
        sb.append(center(cfg.getRestaurantName(), 48)).append("\n");
        if (cfg.getAddress() != null && !cfg.getAddress().isBlank())
            sb.append(center(cfg.getAddress(), 48)).append("\n");
        if (cfg.getPhoneNumber() != null && !cfg.getPhoneNumber().isBlank())
            sb.append(center("Ph: " + cfg.getPhoneNumber(), 48)).append("\n");
        if (cfg.getGstNumber() != null && !cfg.getGstNumber().isBlank())
            sb.append(center("GSTIN: " + cfg.getGstNumber(), 48)).append("\n");
        sb.append(center("INVOICE", 48)).append("\n");
        sb.append(eq).append("\n");
        sb.append(String.format("Invoice No  : %s-SAMPLE%n", cfg.getInvoicePrefix()));
        sb.append(String.format("Date & Time : 15-05-2026 13:00%n"));
        sb.append(String.format("Table No    : 5%n"));
        sb.append(String.format("Customer    : Sample Guest%n"));
        sb.append(sep).append("\n");
        sb.append(String.format("%-26s %5s %10s%n", "Item", "Qty", "Amount"));
        sb.append(sep).append("\n");
        sb.append(String.format("%-26s %5d %10.2f%n", "Butter Chicken", 1, 320.00));
        sb.append(String.format("  @ %s320.00 each%n", cfg.getCurrencySymbol()));
        sb.append(String.format("%-26s %5d %10.2f%n", "Garlic Naan", 2, 100.00));
        sb.append(String.format("  @ %s50.00 each%n", cfg.getCurrencySymbol()));
        sb.append(sep).append("\n");
        sb.append(String.format("%-33s %10.2f%n", "Subtotal:", 420.00));
        sb.append(String.format("%-33s %10.2f%n", "GST @ 18%:", 75.60));
        sb.append(eq).append("\n");
        sb.append(String.format("%-33s %s%10.2f%n", "TOTAL AMOUNT:", cfg.getCurrencySymbol(), 495.60));
        sb.append(eq).append("\n");
        if (cfg.getThankYouMessage() != null && !cfg.getThankYouMessage().isBlank())
            sb.append(center(cfg.getThankYouMessage(), 48)).append("\n");
        if (cfg.getFooterMessage() != null && !cfg.getFooterMessage().isBlank())
            sb.append(center(cfg.getFooterMessage(), 48)).append("\n");
        sb.append(eq).append("\n");
        return sb.toString();
    }

    private boolean validateFields() {
        if (restaurantNameField.getText().trim().isBlank()) {
            showStatus("Restaurant name is required.", false);
            restaurantNameField.requestFocus();
            return false;
        }
        if (currencySymbolField.getText().trim().isBlank()) {
            showStatus("Currency symbol is required.", false);
            return false;
        }
        if (invoicePrefixField.getText().trim().isBlank()) {
            showStatus("Invoice prefix is required.", false);
            return false;
        }
        String gst = gstField.getText().trim();
        if (!gst.isBlank() && !gst.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}")) {
            showStatus("GST number format invalid (expected: 22AAAAA0000A1Z5)", false);
            gstField.requestFocus();
            return false;
        }
        return true;
    }

    private void showStatus(String msg, boolean success) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + (success ? "#27ae60" : "#e74c3c") + ";-fx-font-weight:bold;");
    }

    private String center(String text, int width) {
        if (text == null || text.length() >= width) return text;
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text;
    }

    private String safe(String s) { return s != null ? s : ""; }
}
