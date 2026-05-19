package com.hotel.service;

import com.hotel.entity.Order;
import com.hotel.entity.RestaurantConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Handles all print operations: customer invoice and kitchen order ticket (KOT).
 *
 * First print: shows a printer-picker dialog and remembers the choice via
 * java.util.prefs.Preferences (persisted across app restarts).
 * Subsequent prints: sends directly to the remembered printer without any dialog.
 *
 * Must be called from the JavaFX Application Thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final Font MONO       = Font.font("Courier New", 12);
    private static final Font MONO_BOLD  = Font.font("Courier New", FontWeight.BOLD, 13);
    private static final Font LARGE_BOLD = Font.font("Courier New", FontWeight.BOLD, 18);

    private static final String PREF_PRINTER = "receiptPrinterName";
    private final Preferences prefs = Preferences.userNodeForPackage(PrintService.class);

    private final BillingService          billingService;
    private final RestaurantConfigService configService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens an invoice preview window. The Print button inside the preview
     * handles printer selection (first use) and remembers the choice for future prints.
     */
    public void showInvoicePreview(Order order, Window owner) {
        VBox content = buildInvoicePane(order, configService.getConfig());
        showPreviewWindow("Invoice Preview — " + order.getOrderNumber(), content, owner,
                () -> log.info("Invoice printed for order {}", order.getOrderNumber()));
    }

    /**
     * Opens a KOT preview window. The Print button inside the preview
     * handles printer selection (first use) and remembers the choice for future prints.
     */
    public void showKotPreview(Order order, Window owner) {
        VBox content = buildKotPane(order);
        showPreviewWindow("KOT — " + order.getOrderNumber(), content, owner,
                () -> log.info("KOT printed for order {}", order.getOrderNumber()));
    }

    // ── Preview window ────────────────────────────────────────────────────────

    private void showPreviewWindow(String title, VBox content, Window owner, Runnable onPrinted) {
        Stage previewStage = new Stage();
        previewStage.setTitle(title);
        previewStage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) previewStage.initOwner(owner);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#f5f5f5;");

        Button btnPrint = new Button("🖨  Print");
        btnPrint.setStyle("-fx-background-color:#0f3460;-fx-text-fill:white;-fx-font-size:13;"
                + "-fx-font-weight:bold;-fx-background-radius:7;-fx-padding:8 18;-fx-cursor:hand;");
        Button btnClose = new Button("Close");
        btnClose.setStyle("-fx-background-color:#6c757d;-fx-text-fill:white;-fx-font-size:13;"
                + "-fx-background-radius:7;-fx-padding:8 16;-fx-cursor:hand;");

        btnPrint.setOnAction(e -> {
            Printer printer = getOrSelectPrinter(previewStage);
            if (printer == null) return;
            forceLayout(content);
            executePrint(content, printer, previewStage);
            onPrinted.run();
        });
        btnClose.setOnAction(e -> previewStage.close());

        HBox buttons = new HBox(12, btnPrint, btnClose);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 16, 10, 16));
        buttons.setStyle("-fx-background-color:white;-fx-border-color:#dee2e6;-fx-border-width:1 0 0 0;");

        VBox root = new VBox(scroll, buttons);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        previewStage.setScene(new Scene(root, 480, 680));
        previewStage.show();
    }

    /**
     * Forgets the remembered printer and immediately shows the picker so the user
     * can choose a different one. Called from Settings screen.
     */
    public void changePrinter(Window owner) {
        prefs.remove(PREF_PRINTER);
        Printer chosen = showPrinterPicker(owner);
        if (chosen != null) {
            prefs.put(PREF_PRINTER, chosen.getName());
            showInfo(owner, "Printer set to: " + chosen.getName());
        }
    }

    /** Returns the currently remembered printer name, or null if none saved. */
    public String getRememberedPrinterName() {
        return prefs.get(PREF_PRINTER, null);
    }

    // ── Printer selection ─────────────────────────────────────────────────────

    private Printer getOrSelectPrinter(Window owner) {
        String savedName = prefs.get(PREF_PRINTER, null);

        if (savedName != null) {
            Optional<Printer> found = Printer.getAllPrinters().stream()
                    .filter(p -> p.getName().equals(savedName))
                    .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            // Saved printer no longer available — clear it and show picker
            log.warn("Saved printer '{}' not found, showing printer picker", savedName);
            prefs.remove(PREF_PRINTER);
        }

        Printer chosen = showPrinterPicker(owner);
        if (chosen != null) {
            prefs.put(PREF_PRINTER, chosen.getName());
            log.info("Printer selected and saved: {}", chosen.getName());
        }
        return chosen;
    }

    private Printer showPrinterPicker(Window owner) {
        List<Printer> printers = new ArrayList<>(Printer.getAllPrinters());
        if (printers.isEmpty()) {
            showError(owner, "No printers found. Please connect a printer and try again.");
            return null;
        }

        // Build a clean printer-selection dialog
        Dialog<Printer> dialog = new Dialog<>();
        dialog.setTitle("Select Printer");
        dialog.setHeaderText("Choose the printer for receipts.\nThis selection will be remembered for future prints.");
        if (owner != null) dialog.initOwner(owner);

        // Printer list
        List<String> names = printers.stream().map(Printer::getName).toList();
        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(names));
        listView.setPrefHeight(Math.min(printers.size() * 28 + 16, 220));
        listView.setPrefWidth(420);
        listView.getSelectionModel().selectFirst();

        // Hint label
        Label hint = new Label("Tip: You can change this later from Settings → Change Printer.");
        hint.setStyle("-fx-font-size:11;-fx-text-fill:#6c757d;");

        VBox content = new VBox(10, listView, hint);
        content.setPadding(new Insets(12, 0, 4, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                int idx = listView.getSelectionModel().getSelectedIndex();
                return idx >= 0 ? printers.get(idx) : null;
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    // ── Print execution ───────────────────────────────────────────────────────

    private void executePrint(Node content, Printer printer, Window owner) {
        PrinterJob job = PrinterJob.createPrinterJob(printer);
        if (job == null) {
            showError(owner, "Could not create a print job for \"" + printer.getName()
                    + "\". Please check the printer status.");
            return;
        }

        PageLayout page  = job.getJobSettings().getPageLayout();
        double     pw    = page.getPrintableWidth();
        double     ph    = page.getPrintableHeight();
        double     nodeW = content.getBoundsInLocal().getWidth();
        double     nodeH = content.getBoundsInLocal().getHeight();

        if (nodeW <= 0 || nodeH <= 0) {
            showError(owner, "Print layout failed — could not determine document size.");
            return;
        }

        double scaleX = pw / nodeW;
        double scaleY = ph / nodeH;
        double scale  = Math.min(scaleX, scaleY);

        Scale tx = new Scale(scale, scale);
        content.getTransforms().add(tx);
        boolean printed = job.printPage(content);
        content.getTransforms().remove(tx);

        if (printed) {
            job.endJob();
        } else {
            log.warn("Print page returned false for printer '{}'", printer.getName());
            showError(owner, "Printing failed. Please check the printer and try again.");
        }
    }

    /**
     * Forces a synchronous CSS + layout pass on an off-screen node so its bounds
     * are correct when passed to PrinterJob.printPage().
     */
    private void forceLayout(VBox node) {
        // Wrapping in a Scene triggers the CSS engine; applyCss()+layout() make it synchronous.
        new Scene(node);
        node.applyCss();
        node.layout();
    }

    // ── Invoice layout ────────────────────────────────────────────────────────

    private VBox buildInvoicePane(Order order, RestaurantConfig cfg) {
        VBox pane = new VBox(4);
        pane.setPadding(new Insets(20, 24, 20, 24));
        pane.setStyle("-fx-background-color:white;");
        pane.setPrefWidth(420);

        // Logo
        if (cfg.getLogoPath() != null && !cfg.getLogoPath().isBlank()) {
            try (FileInputStream fis = new FileInputStream(new File(cfg.getLogoPath()))) {
                ImageView logo = new ImageView(new Image(fis));
                logo.setFitWidth(80);
                logo.setFitHeight(80);
                logo.setPreserveRatio(true);
                StackPane logoBox = new StackPane(logo);
                logoBox.setAlignment(Pos.CENTER);
                logoBox.setPadding(new Insets(0, 0, 8, 0));
                pane.getChildren().add(logoBox);
            } catch (Exception ignored) {}
        }

        // Header
        pane.getChildren().add(centeredText(cfg.getRestaurantName(), LARGE_BOLD));
        if (notBlank(cfg.getAddress()))     pane.getChildren().add(centeredText(cfg.getAddress(), MONO));
        if (notBlank(cfg.getPhoneNumber())) pane.getChildren().add(centeredText("Ph: " + cfg.getPhoneNumber(), MONO));
        if (notBlank(cfg.getGstNumber()))   pane.getChildren().add(centeredText("GSTIN: " + cfg.getGstNumber(), MONO));
        if (notBlank(cfg.getFssaiNumber())) pane.getChildren().add(centeredText("FSSAI: " + cfg.getFssaiNumber(), MONO));
        pane.getChildren().add(centeredText("─── INVOICE ───", MONO_BOLD));
        pane.getChildren().add(separator());

        // Meta
        String cur = cfg.getCurrencySymbol();
        pane.getChildren().add(infoRow("Invoice No",  cfg.getInvoicePrefix() + "-" + order.getOrderNumber()));
        pane.getChildren().add(infoRow("Date & Time", order.getCreatedAt().format(DT_FMT)));
        pane.getChildren().add(infoRow("Table No",    String.valueOf(order.getTableNumber())));
        if (notBlank(order.getCustomerName()))
            pane.getChildren().add(infoRow("Customer", order.getCustomerName()));
        if (order.getCreatedBy() != null)
            pane.getChildren().add(infoRow("Served by", order.getCreatedBy().getFullName()));
        pane.getChildren().add(separator());

        // Column headers
        pane.getChildren().add(itemRow("Item", "Qty", "Amount", true));
        pane.getChildren().add(separator());

        // Items
        order.getItems().forEach(item -> {
            pane.getChildren().add(itemRow(
                    item.getMenuItem().getName(),
                    String.valueOf(item.getQuantity()),
                    cur + String.format("%.2f", item.getTotalPrice()), false));
            pane.getChildren().add(subRow("@ " + cur + String.format("%.2f", item.getUnitPrice()) + " each"));
        });

        pane.getChildren().add(separator());
        pane.getChildren().add(totalRow("Subtotal:", cur + String.format("%.2f", order.getSubtotal()), false));
        if (order.getTaxRate().signum() > 0) {
            pane.getChildren().add(totalRow(
                    String.format("GST @ %.0f%%:", order.getTaxRate()),
                    cur + String.format("%.2f", order.getTaxAmount()), false));
        }
        pane.getChildren().add(separator());
        pane.getChildren().add(totalRow("TOTAL:", cur + String.format("%.2f", order.getTotalAmount()), true));
        pane.getChildren().add(separator());

        // Footer
        if (notBlank(cfg.getThankYouMessage()))
            pane.getChildren().add(centeredText(cfg.getThankYouMessage(), MONO));
        if (notBlank(cfg.getFooterMessage()))
            pane.getChildren().add(centeredText(cfg.getFooterMessage(), MONO));

        return pane;
    }

    // ── KOT layout ────────────────────────────────────────────────────────────

    private VBox buildKotPane(Order order) {
        VBox pane = new VBox(6);
        pane.setPadding(new Insets(20, 24, 20, 24));
        pane.setStyle("-fx-background-color:white;");
        pane.setPrefWidth(380);

        pane.getChildren().add(centeredText("*** KITCHEN ORDER TICKET ***", MONO_BOLD));
        pane.getChildren().add(separator());
        pane.getChildren().add(infoRow("Order",  order.getOrderNumber()));
        pane.getChildren().add(infoRow("Table",  String.valueOf(order.getTableNumber())));
        pane.getChildren().add(infoRow("Time",   order.getCreatedAt().format(DT_FMT)));
        if (notBlank(order.getCustomerName()))
            pane.getChildren().add(infoRow("Guest", order.getCustomerName()));
        pane.getChildren().add(separator());
        pane.getChildren().add(itemRow("ITEM", "QTY", "", true));
        pane.getChildren().add(separator());

        order.getItems().forEach(item -> {
            pane.getChildren().add(itemRow(
                    item.getMenuItem().getName(),
                    "x" + item.getQuantity(), "", false));
            if (notBlank(item.getNotes())) {
                Text note = new Text("  >> " + item.getNotes());
                note.setFont(Font.font("Courier New", 11));
                pane.getChildren().add(note);
            }
        });

        pane.getChildren().add(separator());
        return pane;
    }

    // ── Node builders ─────────────────────────────────────────────────────────

    private Text centeredText(String txt, Font font) {
        Text t = new Text(txt);
        t.setFont(font);
        t.setWrappingWidth(372);
        t.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return t;
    }

    private HBox infoRow(String label, String value) {
        Text l = styledText(label + ":  ", MONO);
        Text v = styledText(value, MONO_BOLD);
        HBox row = new HBox(l, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox itemRow(String name, String qty, String price, boolean header) {
        Font font = header ? MONO_BOLD : MONO;
        Text nameText  = styledText(padRight(name, 24), font);
        Text qtyText   = styledText(padLeft(qty, 5),    font);
        Text priceText = styledText(padLeft(price, 10), font);
        HBox row = new HBox(nameText, qtyText, priceText);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox totalRow(String label, String value, boolean bold) {
        Font font = bold ? MONO_BOLD : MONO;
        Text l = styledText(padRight(label, 33), font);
        Text v = styledText(padLeft(value, 10),  font);
        HBox row = new HBox(l, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Text subRow(String text) {
        return styledText("  " + text, Font.font("Courier New", 11));
    }

    private javafx.scene.shape.Line separator() {
        javafx.scene.shape.Line line = new javafx.scene.shape.Line(0, 0, 372, 0);
        line.setStroke(javafx.scene.paint.Color.DARKGRAY);
        return line;
    }

    private Text styledText(String content, Font font) {
        Text t = new Text(content);
        t.setFont(font);
        return t;
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s.length() > n ? s.substring(0, n - 2) + ".." : s);
    }

    private String padLeft(String s, int n) {
        if (s == null) s = "";
        return String.format("%" + n + "s", s);
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private void showError(Window owner, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("Print Error");
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }

    private void showInfo(Window owner, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle("Printer");
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }
}
