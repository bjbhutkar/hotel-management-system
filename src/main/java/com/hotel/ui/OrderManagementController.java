package com.hotel.ui;

import com.hotel.entity.MenuItem;
import com.hotel.entity.Order;
import com.hotel.entity.OrderItem;
import com.hotel.service.MenuService;
import com.hotel.service.OrderService;
import com.hotel.service.PrintService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderManagementController {

    /* ---- Left panel: active orders list ---- */
    @FXML private TableView<Order>              ordersTable;
    @FXML private TableColumn<Order, String>    orderNumCol;
    @FXML private TableColumn<Order, Integer>   tableNumCol;
    @FXML private TableColumn<Order, String>    statusCol;
    @FXML private TableColumn<Order, String>    totalCol;

    /* ---- Right panel: order details ---- */
    @FXML private TextField     tableNumberField;
    @FXML private TextField     customerNameField;
    @FXML private ComboBox<String>    categoryCombo;
    @FXML private TextField           menuSearchField;
    @FXML private ListView<MenuItem>  menuItemsListView;
    @FXML private Spinner<Integer>    quantitySpinner;

    @FXML private TableView<OrderItem>          orderItemsTable;
    @FXML private TableColumn<OrderItem, String> itemNameCol;
    @FXML private TableColumn<OrderItem, Integer> itemQtyCol;
    @FXML private TableColumn<OrderItem, String> itemPriceCol;

    @FXML private Label    subtotalLabel;
    @FXML private Label    taxLabel;
    @FXML private Label    totalLabel;
    @FXML private Label    statusLabel;
    @FXML private CheckBox applyGstCheckbox;
    @FXML private TextField gstRateField;

    @FXML private Button addItemButton;
    @FXML private Button removeItemButton;
    @FXML private Button editOrderButton;
    @FXML private Button cancelOrderButton;
    @FXML private Button saveOrderButton;
    @FXML private Button printInvoiceButton;
    @FXML private Button printKotButton;

    private final OrderService orderService;
    private final MenuService  menuService;
    private final PrintService printService;
    private final StageManager stageManager;

    private Order currentOrder;
    private List<MenuItem> currentCategoryItems = new ArrayList<>();
    private static final String ALL_CATEGORIES = "All Categories";

    @FXML
    public void initialize() {
        // Orders table
        orderNumCol.setCellValueFactory(new PropertyValueFactory<>("orderNumber"));
        tableNumCol.setCellValueFactory(new PropertyValueFactory<>("tableNumber"));
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        totalCol.setCellValueFactory(d -> new SimpleStringProperty("₹" +
                d.getValue().getTotalAmount().setScale(2)));

        // Order items table
        itemNameCol.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getMenuItem().getName()));
        itemQtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        itemPriceCol.setCellValueFactory(d ->
                new SimpleStringProperty("₹" + d.getValue().getTotalPrice().setScale(2)));

        // Category combo – "All Categories" is always first
        List<String> allCats = new ArrayList<>(List.of(ALL_CATEGORIES));
        allCats.addAll(menuService.getAllCategories());
        categoryCombo.setItems(FXCollections.observableArrayList(allCats));

        categoryCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, cat) -> { if (cat != null) loadMenuForCategory(cat); });
        categoryCombo.getSelectionModel().selectFirst();

        // Search field filters the current category list
        menuSearchField.textProperty().addListener((obs, o, n) -> applyMenuSearch());

        // GST rate field: apply on Enter or focus lost
        gstRateField.setOnAction(e -> applyGstToCurrentOrder());
        gstRateField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) applyGstToCurrentOrder();
        });

        // Quantity spinner
        quantitySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 99, 1));

        // Menu items display
        menuItemsListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(MenuItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + "  ₹" + item.getPrice());
            }
        });

        setOrderActionsEnabled(false);
        loadActiveOrders();

        ordersTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, order) -> { if (order != null) loadOrderDetails(order); });
    }

    private void loadActiveOrders() {
        ordersTable.setItems(FXCollections.observableArrayList(orderService.getActiveOrders()));
        ordersTable.refresh();
    }

    private void loadMenuForCategory(String category) {
        currentCategoryItems = ALL_CATEGORIES.equals(category)
                ? menuService.getAvailableMenuItems()
                : menuService.getMenuItemsByCategory(category);
        applyMenuSearch();
    }

    private void applyMenuSearch() {
        String query = menuSearchField.getText().trim().toLowerCase();
        List<MenuItem> filtered = query.isEmpty()
                ? currentCategoryItems
                : currentCategoryItems.stream()
                        .filter(item -> item.getName().toLowerCase().contains(query))
                        .collect(Collectors.toList());
        menuItemsListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void loadOrderDetails(Order order) {
        currentOrder = orderService.getOrderById(order.getId()).orElse(order);
        tableNumberField.setText(String.valueOf(currentOrder.getTableNumber()));
        customerNameField.setText(currentOrder.getCustomerName() != null
                ? currentOrder.getCustomerName() : "");
        refreshOrderItems();
        setOrderActionsEnabled(true);
        boolean isLocked = currentOrder.getStatus() == Order.OrderStatus.COMPLETED
                        || currentOrder.getStatus() == Order.OrderStatus.BILLED;
        addItemButton.setDisable(isLocked);
        removeItemButton.setDisable(isLocked);
        editOrderButton.setVisible(isLocked);
        editOrderButton.setManaged(isLocked);
        // Billed orders are already saved — re-saving makes no sense until re-opened
        saveOrderButton.setDisable(currentOrder.getStatus() == Order.OrderStatus.BILLED);
        // Sync GST controls from the order's stored tax rate
        boolean hasGst = currentOrder.getTaxRate().compareTo(BigDecimal.ZERO) > 0;
        applyGstCheckbox.setSelected(hasGst);
        gstRateField.setDisable(!hasGst);
        gstRateField.setText(hasGst
                ? currentOrder.getTaxRate().stripTrailingZeros().toPlainString() : "");
    }

    private void refreshOrderItems() {
        if (currentOrder == null) return;
        orderItemsTable.setItems(FXCollections.observableArrayList(currentOrder.getItems()));
        subtotalLabel.setText("₹" + currentOrder.getSubtotal().setScale(2));
        taxLabel.setText("₹" + currentOrder.getTaxAmount().setScale(2));
        totalLabel.setText("₹" + currentOrder.getTotalAmount().setScale(2));
    }

    @FXML
    public void handleCreateOrder() {
        String tableText = tableNumberField.getText().trim();
        try {
            int tableNum = tableText.isEmpty() ? 1 : Integer.parseInt(tableText);
            String customer = customerNameField.getText().trim();
            currentOrder = orderService.createOrder(tableNum, customer);
            loadActiveOrders();
            reSelectCurrentOrder();
            showStatus("Order " + currentOrder.getOrderNumber() + " created.", false);
        } catch (NumberFormatException e) {
            showStatus("Table number must be a number.", true);
        }
    }

    @FXML
    public void handleAddItem() {
        if (currentOrder == null) { showStatus("Create or select an order first.", true); return; }
        MenuItem selected = menuItemsListView.getSelectionModel().getSelectedItem();
        if (selected == null)     { showStatus("Select a menu item to add.", true); return; }

        int qty = quantitySpinner.getValue();
        currentOrder = orderService.addItemToOrder(currentOrder.getId(), selected, qty, "");
        refreshOrderItems();
        loadActiveOrders();
        reSelectCurrentOrder();
        showStatus("Added: " + selected.getName() + " × " + qty, false);
    }

    @FXML
    public void handleRemoveItem() {
        if (currentOrder == null) return;
        OrderItem sel = orderItemsTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showStatus("Select an item to remove.", true); return; }
        currentOrder = orderService.removeItemFromOrder(currentOrder.getId(), sel.getId());
        refreshOrderItems();
        loadActiveOrders();
        reSelectCurrentOrder();
        showStatus("Item removed.", false);
    }

    @FXML
    public void handleCancelOrder() {
        if (currentOrder == null) return;
        TextInputDialog dialog = new TextInputDialog("Customer request");
        dialog.setTitle("Cancel Order");
        dialog.setHeaderText("Cancel Order: " + currentOrder.getOrderNumber());
        dialog.setContentText("Cancellation reason:");
        dialog.showAndWait().ifPresent(reason -> {
            if (!reason.isBlank()) {
                orderService.cancelOrder(currentOrder.getId(), reason);
                clearOrderPanel();
                loadActiveOrders();
                showStatus("Order cancelled.", false);
            }
        });
    }

    @FXML
    public void handleEditOrder() {
        currentOrder = orderService.reopenOrder(currentOrder.getId());
        loadActiveOrders();
        reSelectCurrentOrder();
        showStatus("Order " + currentOrder.getOrderNumber() + " reopened — status set to In Progress.", false);
    }

    @FXML
    public void handleSaveOrder() {
        if (currentOrder == null) return;
        if (currentOrder.getItems().isEmpty()) {
            showStatus("Add items before saving.", true);
            return;
        }
        currentOrder = orderService.markAsBilled(currentOrder.getId());
        showStatus("Order " + currentOrder.getOrderNumber() + " saved.", false);
        clearOrderPanel();
        loadActiveOrders();
    }

    @FXML
    public void handleRefresh() {
        loadActiveOrders();
        clearOrderPanel();
    }

    @FXML
    public void handlePrintInvoice() {
        if (currentOrder == null) { showStatus("Select an order to print.", true); return; }
        Order order = orderService.getOrderWithDetails(currentOrder.getId()).orElse(currentOrder);
        printService.showInvoicePreview(order,
                saveOrderButton.getScene() != null ? saveOrderButton.getScene().getWindow() : null);
    }

    @FXML
    public void handlePrintKot() {
        if (currentOrder == null) { showStatus("Select an order to print KOT.", true); return; }
        Order order = orderService.getOrderWithDetails(currentOrder.getId()).orElse(currentOrder);
        printService.showKotPreview(order,
                saveOrderButton.getScene() != null ? saveOrderButton.getScene().getWindow() : null);
    }

    @FXML
    public void handleBack() throws IOException {
        stageManager.showDashboard();
    }

    // -----------------------------------------------------------------------

    private void reSelectCurrentOrder() {
        if (currentOrder == null) return;
        Long id = currentOrder.getId();
        for (int i = 0; i < ordersTable.getItems().size(); i++) {
            if (ordersTable.getItems().get(i).getId().equals(id)) {
                ordersTable.getSelectionModel().select(i);
                break;
            }
        }
    }

    private void clearOrderPanel() {
        currentOrder = null;
        tableNumberField.clear();
        customerNameField.clear();
        orderItemsTable.setItems(FXCollections.emptyObservableList());
        subtotalLabel.setText("₹0.00");
        taxLabel.setText("₹0.00");
        totalLabel.setText("₹0.00");
        applyGstCheckbox.setSelected(false);
        gstRateField.setDisable(true);
        gstRateField.clear();
        ordersTable.getSelectionModel().clearSelection();
        setOrderActionsEnabled(false);
    }

    private void setOrderActionsEnabled(boolean enabled) {
        addItemButton.setDisable(!enabled);
        removeItemButton.setDisable(!enabled);
        cancelOrderButton.setDisable(!enabled);
        saveOrderButton.setDisable(!enabled);
        printInvoiceButton.setDisable(!enabled);
        printKotButton.setDisable(!enabled);
        if (!enabled) {
            editOrderButton.setVisible(false);
            editOrderButton.setManaged(false);
        }
    }

    @FXML
    public void handleGstToggle() {
        boolean apply = applyGstCheckbox.isSelected();
        gstRateField.setDisable(!apply);
        if (!apply) {
            gstRateField.clear();
        }
        applyGstToCurrentOrder();
    }

    private void applyGstToCurrentOrder() {
        if (currentOrder == null) return;
        BigDecimal rate = BigDecimal.ZERO;
        if (applyGstCheckbox.isSelected()) {
            String text = gstRateField.getText().trim();
            if (text.isEmpty()) {
                showStatus("Enter a GST percentage.", true);
                return;
            }
            try {
                rate = new BigDecimal(text);
                if (rate.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showStatus("GST % must be a positive number.", true);
                return;
            }
        }
        currentOrder = orderService.updateTaxRate(currentOrder.getId(), rate);
        refreshOrderItems();
        loadActiveOrders();
        reSelectCurrentOrder();
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill:#e74c3c;" : "-fx-text-fill:#27ae60;");
    }
}
