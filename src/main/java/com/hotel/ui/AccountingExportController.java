package com.hotel.ui;

import com.hotel.entity.Order;
import com.hotel.service.AccountingExportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingExportController {

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;

    @FXML private Label totalOrdersLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label cgstLabel;
    @FXML private Label sgstLabel;
    @FXML private Label totalGstLabel;
    @FXML private Label totalRevenueLabel;
    @FXML private Label orderCountLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<Order>              ordersTable;
    @FXML private TableColumn<Order, String>    colOrderNum;
    @FXML private TableColumn<Order, String>    colDate;
    @FXML private TableColumn<Order, String>    colCustomer;
    @FXML private TableColumn<Order, String>    colTable;
    @FXML private TableColumn<Order, String>    colSubtotal;
    @FXML private TableColumn<Order, String>    colTaxRate;
    @FXML private TableColumn<Order, String>    colCgst;
    @FXML private TableColumn<Order, String>    colSgst;
    @FXML private TableColumn<Order, String>    colTotal;

    @FXML private Button exportExcelBtn;
    @FXML private Button exportTallyBtn;

    private final AccountingExportService exportService;
    private final StageManager            stageManager;

    private List<Order>  loadedOrders;
    private LocalDate    loadedFrom;
    private LocalDate    loadedTo;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @FXML
    public void initialize() {
        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(today.withDayOfMonth(1));
        toDatePicker.setValue(today);
        configureTableColumns();
    }

    @FXML
    public void handleLoadOrders() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to   = toDatePicker.getValue();

        if (from == null || to == null) {
            showStatus("Please select both From and To dates.", false);
            return;
        }
        if (from.isAfter(to)) {
            showStatus("'From' date must be before or equal to 'To' date.", false);
            return;
        }

        loadedOrders = exportService.getBilledOrders(from, to);
        loadedFrom   = from;
        loadedTo     = to;

        updateSummaryCards();
        ordersTable.setItems(FXCollections.observableArrayList(loadedOrders));
        orderCountLabel.setText("(" + loadedOrders.size() + " orders)");

        boolean hasData = !loadedOrders.isEmpty();
        exportExcelBtn.setDisable(!hasData);
        exportTallyBtn.setDisable(!hasData);

        if (hasData) {
            showStatus(loadedOrders.size() + " billed orders loaded for "
                    + from + " to " + to + ".", true);
        } else {
            showStatus("No billed orders found for the selected period.", false);
        }
    }

    @FXML
    public void handleExportExcel() {
        if (loadedOrders == null || loadedOrders.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Excel Report");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));
        chooser.setInitialFileName("accounting_" + loadedFrom + "_to_" + loadedTo + ".xlsx");

        File file = chooser.showSaveDialog(ordersTable.getScene().getWindow());
        if (file == null) return;

        try {
            exportService.exportToExcel(loadedOrders, loadedFrom, loadedTo, file);
            showStatus("Excel report saved: " + file.getName(), true);
        } catch (Exception e) {
            log.error("Excel export failed", e);
            showStatus("Export failed: " + e.getMessage(), false);
        }
    }

    @FXML
    public void handleExportTally() {
        if (loadedOrders == null || loadedOrders.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Tally XML");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Tally XML Files (*.xml)", "*.xml"));
        chooser.setInitialFileName("tally_vouchers_" + loadedFrom + "_to_" + loadedTo + ".xml");

        File file = chooser.showSaveDialog(ordersTable.getScene().getWindow());
        if (file == null) return;

        try {
            exportService.exportToTallyXml(loadedOrders, file);
            showStatus("Tally XML saved: " + file.getName()
                    + ". Import via Tally → Gateway → Import Data → Vouchers.", true);
        } catch (Exception e) {
            log.error("Tally XML export failed", e);
            showStatus("Export failed: " + e.getMessage(), false);
        }
    }

    @FXML
    public void handleBack() throws Exception {
        stageManager.showDashboard();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void configureTableColumns() {
        colOrderNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getOrderNumber()));

        colDate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCreatedAt() != null
                        ? d.getValue().getCreatedAt().format(DISPLAY_FMT) : ""));

        colCustomer.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCustomerName() != null
                        ? d.getValue().getCustomerName() : "Walk-in"));

        colTable.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTableNumber() != null
                        ? String.valueOf(d.getValue().getTableNumber()) : ""));

        colSubtotal.setCellValueFactory(d -> new SimpleStringProperty(
                "₹" + fmt2(d.getValue().getSubtotal())));

        colTaxRate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTaxRate() != null
                        ? d.getValue().getTaxRate().stripTrailingZeros().toPlainString() + "%" : "0%"));

        colCgst.setCellValueFactory(d -> {
            BigDecimal cgst = d.getValue().getTaxAmount()
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            return new SimpleStringProperty("₹" + fmt2(cgst));
        });

        colSgst.setCellValueFactory(d -> {
            BigDecimal cgst = d.getValue().getTaxAmount()
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal sgst = d.getValue().getTaxAmount().subtract(cgst);
            return new SimpleStringProperty("₹" + fmt2(sgst));
        });

        colTotal.setCellValueFactory(d -> new SimpleStringProperty(
                "₹" + fmt2(d.getValue().getTotalAmount())));
    }

    private void updateSummaryCards() {
        if (loadedOrders == null || loadedOrders.isEmpty()) {
            totalOrdersLabel.setText("0");
            subtotalLabel.setText("₹0.00");
            cgstLabel.setText("₹0.00");
            sgstLabel.setText("₹0.00");
            totalGstLabel.setText("₹0.00");
            totalRevenueLabel.setText("₹0.00");
            return;
        }

        BigDecimal subtotal  = loadedOrders.stream().map(Order::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax       = loadedOrders.stream().map(Order::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total     = loadedOrders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cgst      = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal sgst      = tax.subtract(cgst);

        totalOrdersLabel.setText(String.valueOf(loadedOrders.size()));
        subtotalLabel.setText("₹" + fmt2(subtotal));
        cgstLabel.setText("₹" + fmt2(cgst));
        sgstLabel.setText("₹" + fmt2(sgst));
        totalGstLabel.setText("₹" + fmt2(tax));
        totalRevenueLabel.setText("₹" + fmt2(total));
    }

    private String fmt2(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
    }

    private void showStatus(String msg, boolean success) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + (success ? "#27ae60" : "#e74c3c") + ";-fx-font-weight:bold;");
    }
}
