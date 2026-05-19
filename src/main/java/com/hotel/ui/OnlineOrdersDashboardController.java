package com.hotel.ui;

import com.hotel.delivery.entity.OnlineOrder;
import com.hotel.delivery.entity.OnlineOrderItem;
import com.hotel.delivery.enums.OnlineOrderStatus;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.event.NewOnlineOrderEvent;
import com.hotel.delivery.event.OrderStatusChangedEvent;
import com.hotel.delivery.service.DeliveryIntegrationService;
import com.hotel.delivery.service.OnlineOrderProcessingService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Toolkit;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineOrdersDashboardController {

    // ── Injected ──────────────────────────────────────────────────────────────
    private final DeliveryIntegrationService    deliveryIntegrationService;
    private final OnlineOrderProcessingService  orderProcessingService;
    private final StageManager                  stageManager;

    // ── FXML — orders table ───────────────────────────────────────────────────
    @FXML private TableView<OnlineOrder>            ordersTable;
    @FXML private TableColumn<OnlineOrder, String>  colOrderNum;
    @FXML private TableColumn<OnlineOrder, String>  colPlatform;
    @FXML private TableColumn<OnlineOrder, String>  colCustomer;
    @FXML private TableColumn<OnlineOrder, String>  colStatus;
    @FXML private TableColumn<OnlineOrder, String>  colTotal;
    @FXML private TableColumn<OnlineOrder, String>  colTime;

    // ── FXML — detail panel ───────────────────────────────────────────────────
    @FXML private Label         lblOrderNumber;
    @FXML private Label         lblPlatform;
    @FXML private Label         lblCustomerName;
    @FXML private Label         lblPhone;
    @FXML private Label         lblAddress;
    @FXML private Label         lblPayment;
    @FXML private Label         lblNotes;
    @FXML private TableView<OnlineOrderItem>            itemsTable;
    @FXML private TableColumn<OnlineOrderItem, String>  colItemName;
    @FXML private TableColumn<OnlineOrderItem, String>  colItemQty;
    @FXML private TableColumn<OnlineOrderItem, String>  colItemPrice;
    @FXML private Label         lblItemsTotal;
    @FXML private Label         lblTax;
    @FXML private Label         lblDelivery;
    @FXML private Label         lblGrandTotal;
    @FXML private Label         lblNewBadge;

    // ── FXML — action buttons ─────────────────────────────────────────────────
    @FXML private Button  btnAccept;
    @FXML private Button  btnReject;
    @FXML private Button  btnPreparing;
    @FXML private Button  btnReady;
    @FXML private Button  btnConfig;

    private final ObservableList<OnlineOrder> orderRows = FXCollections.observableArrayList();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupOrdersTable();
        setupItemsTable();
        ordersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> showDetail(sel));
        refreshOrders();
    }

    // ── Spring event listeners (any thread → UI thread via runLater) ──────────

    @EventListener
    public void onNewOrder(NewOnlineOrderEvent event) {
        Platform.runLater(() -> {
            refreshOrders();
            playNotificationBeep();
            showNewOrderAlert(event.getOrder());
        });
    }

    @EventListener
    public void onStatusChanged(OrderStatusChangedEvent event) {
        Platform.runLater(this::refreshOrders);
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    public void handleAccept() {
        OnlineOrder selected = selected();
        if (selected == null) return;

        Dialog<Integer> dialog = buildPrepTimeDialog();
        dialog.showAndWait().ifPresent(prepTime -> {
            try {
                deliveryIntegrationService.acceptOrder(selected.getId(), prepTime);
                refreshOrders();
            } catch (Exception e) {
                showError("Accept failed", e.getMessage());
            }
        });
    }

    @FXML
    public void handleReject() {
        OnlineOrder selected = selected();
        if (selected == null) return;

        TextInputDialog dialog = new TextInputDialog("Out of stock");
        dialog.setTitle("Reject Order");
        dialog.setHeaderText("Reject order " + selected.getInternalOrderNumber() + "?");
        dialog.setContentText("Reason:");
        applyDialogStyle(dialog);
        dialog.showAndWait().ifPresent(reason -> {
            try {
                deliveryIntegrationService.rejectOrder(selected.getId(), reason);
                refreshOrders();
            } catch (Exception e) {
                showError("Reject failed", e.getMessage());
            }
        });
    }

    @FXML
    public void handleMarkPreparing() {
        updateSelectedStatus(OnlineOrderStatus.PREPARING);
    }

    @FXML
    public void handleMarkReady() {
        updateSelectedStatus(OnlineOrderStatus.READY);
    }

    @FXML
    public void handleRefresh() {
        refreshOrders();
    }

    @FXML
    public void handleBack() throws IOException {
        stageManager.showDashboard();
    }

    @FXML
    public void handleOpenConfig() throws IOException {
        stageManager.showPlatformConfig();
    }

    @FXML
    public void handleMenuSync() throws IOException {
        stageManager.showMenuSync();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setupOrdersTable() {
        colOrderNum.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getInternalOrderNumber()));
        colPlatform.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPlatformType().getDisplayName()));
        colCustomer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        colTotal   .setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getGrandTotal().setScale(2, java.math.RoundingMode.HALF_UP)));
        colTime    .setCellValueFactory(c -> {
            String t = c.getValue().getPlacedAt() != null
                    ? c.getValue().getPlacedAt().format(TIME_FMT) : "—";
            return new SimpleStringProperty(t);
        });
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getDisplayName()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                OnlineOrder row = getTableView().getItems().get(getIndex());
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + statusColor(row.getStatus()) + ";");
            }
        });
        ordersTable.setItems(orderRows);
        ordersTable.setPlaceholder(new Label("No active online orders"));
    }

    private void setupItemsTable() {
        colItemName .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItemName()));
        colItemQty  .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQuantity())));
        colItemPrice.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getTotalPrice().setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    private void refreshOrders() {
        List<OnlineOrder> orders = orderProcessingService.getAllOrdersForDashboard();
        orderRows.setAll(orders);
        long newCount = orders.stream()
                .filter(o -> o.getStatus() == OnlineOrderStatus.NEW).count();
        if (lblNewBadge != null) {
            lblNewBadge.setText(newCount > 0 ? String.valueOf(newCount) : "");
            lblNewBadge.setVisible(newCount > 0);
        }
    }

    private void showDetail(OnlineOrder order) {
        if (order == null) {
            clearDetail();
            return;
        }
        lblOrderNumber .setText(order.getInternalOrderNumber());
        lblPlatform    .setText(order.getPlatformType().getDisplayName());
        lblCustomerName.setText(nullSafe(order.getCustomerName()));
        lblPhone       .setText(nullSafe(order.getCustomerPhone()));
        lblAddress     .setText(nullSafe(order.getDeliveryAddress()));
        lblPayment     .setText(nullSafe(order.getPaymentMode()) + (order.isPrepaid() ? " (Prepaid)" : " (COD)"));
        lblNotes       .setText(nullSafe(order.getCustomerNotes()));
        itemsTable.setItems(FXCollections.observableArrayList(order.getItems()));
        lblItemsTotal  .setText("₹" + fmt(order.getItemsTotal()));
        lblTax         .setText("₹" + fmt(order.getTaxAmount()));
        lblDelivery    .setText("₹" + fmt(order.getDeliveryCharge()));
        lblGrandTotal  .setText("₹" + fmt(order.getGrandTotal()));
        updateActionButtons(order.getStatus());
    }

    private void updateActionButtons(OnlineOrderStatus status) {
        btnAccept   .setDisable(status != OnlineOrderStatus.NEW);
        btnReject   .setDisable(status != OnlineOrderStatus.NEW);
        btnPreparing.setDisable(status != OnlineOrderStatus.ACCEPTED);
        btnReady    .setDisable(status != OnlineOrderStatus.PREPARING);
    }

    private void clearDetail() {
        lblOrderNumber.setText("—");
        lblPlatform.setText("—");
        lblCustomerName.setText("—");
        lblPhone.setText("—");
        lblAddress.setText("—");
        lblPayment.setText("—");
        lblNotes.setText("—");
        itemsTable.setItems(FXCollections.emptyObservableList());
        lblItemsTotal.setText("₹0.00");
        lblTax.setText("₹0.00");
        lblDelivery.setText("₹0.00");
        lblGrandTotal.setText("₹0.00");
        btnAccept.setDisable(true);
        btnReject.setDisable(true);
        btnPreparing.setDisable(true);
        btnReady.setDisable(true);
    }

    private void updateSelectedStatus(OnlineOrderStatus newStatus) {
        OnlineOrder selected = selected();
        if (selected == null) return;
        try {
            deliveryIntegrationService.updateStatus(selected.getId(), newStatus);
            refreshOrders();
        } catch (Exception e) {
            showError("Status update failed", e.getMessage());
        }
    }

    private OnlineOrder selected() {
        OnlineOrder sel = ordersTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showError("No selection", "Please select an order first."); }
        return sel;
    }

    private void playNotificationBeep() {
        try { Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
    }

    private void showNewOrderAlert(OnlineOrder order) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("New Online Order!");
        alert.setHeaderText("New order from " + order.getPlatformType().getDisplayName());
        alert.setContentText(
                "Order: " + order.getInternalOrderNumber() + "\n" +
                "Customer: " + nullSafe(order.getCustomerName()) + "\n" +
                "Total: ₹" + fmt(order.getGrandTotal())
        );
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
        alert.show();
    }

    private Dialog<Integer> buildPrepTimeDialog() {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Accept Order");
        dialog.setHeaderText("Set preparation time");
        ButtonType acceptBtn = new ButtonType("Accept", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(acceptBtn, ButtonType.CANCEL);
        Spinner<Integer> spinner = new Spinner<>(5, 90, 20, 5);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        Label lbl = new Label("Preparation time (minutes):");
        HBox box = new HBox(10, lbl, spinner);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(btn -> btn == acceptBtn ? spinner.getValue() : null);
        applyDialogStyle(dialog);
        return dialog;
    }

    private void applyDialogStyle(Dialog<?> dialog) {
        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignored) {}
    }

    private void showError(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private String statusColor(OnlineOrderStatus status) {
        return switch (status) {
            case NEW       -> "#e74c3c";
            case ACCEPTED  -> "#2980b9";
            case PREPARING -> "#e67e22";
            case READY     -> "#27ae60";
            case PICKED_UP -> "#8e44ad";
            case DELIVERED -> "#16a085";
            default        -> "#7f8c8d";
        };
    }

    private String nullSafe(String s)       { return s != null ? s : "—"; }
    private String fmt(BigDecimal bd)        { return bd != null ? bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00"; }
}
