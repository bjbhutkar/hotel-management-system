package com.hotel.ui;

import com.hotel.entity.MenuItem;
import com.hotel.service.MenuService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class MenuManagementController {

    @FXML private TableView<MenuItem>            menuTable;
    @FXML private TableColumn<MenuItem, Long>    idCol;
    @FXML private TableColumn<MenuItem, String>  nameCol;
    @FXML private TableColumn<MenuItem, String>  categoryCol;
    @FXML private TableColumn<MenuItem, String>  priceCol;
    @FXML private TableColumn<MenuItem, String>  statusCol;

    @FXML private TextField  nameField;
    @FXML private TextField  categoryField;
    @FXML private TextField  priceField;
    @FXML private TextArea   descriptionArea;
    @FXML private CheckBox   availableCheck;
    @FXML private Button     deleteButton;
    @FXML private Label      statusLabel;

    private final MenuService  menuService;
    private final StageManager stageManager;

    private MenuItem selectedItem;

    @FXML
    public void initialize() {
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        priceCol.setCellValueFactory(data ->
                new SimpleStringProperty("₹" + data.getValue().getPrice().setScale(2)));
        statusCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isAvailable() ? "✔ Available" : "✖ Unavailable"));

        deleteButton.setDisable(true);
        loadMenuItems();

        menuTable.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            selectedItem = item;
            deleteButton.setDisable(item == null);
            if (item != null) populateForm(item);
        });
    }

    private void loadMenuItems() {
        menuTable.setItems(FXCollections.observableArrayList(menuService.getAllMenuItems()));
    }

    private void populateForm(MenuItem item) {
        nameField.setText(item.getName());
        categoryField.setText(item.getCategory());
        priceField.setText(item.getPrice().toPlainString());
        descriptionArea.setText(item.getDescription() != null ? item.getDescription() : "");
        availableCheck.setSelected(item.isAvailable());
    }

    @FXML
    public void handleSave() {
        String name     = nameField.getText().trim();
        String category = categoryField.getText().trim();
        String priceStr = priceField.getText().trim();
        String desc     = descriptionArea.getText().trim();
        boolean avail   = availableCheck.isSelected();

        if (name.isEmpty() || category.isEmpty() || priceStr.isEmpty()) {
            showStatus("Name, category, and price are required.", true);
            return;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
            if (price.compareTo(BigDecimal.ZERO) < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showStatus("Enter a valid positive price.", true);
            return;
        }

        try {
            if (selectedItem == null) {
                menuService.addMenuItem(name, desc, category, price);
                showStatus("Item '" + name + "' added successfully.", false);
            } else {
                menuService.updateMenuItem(selectedItem.getId(), name, desc, category, price, avail);
                showStatus("Item '" + name + "' updated.", false);
            }
            handleClear();
            loadMenuItems();
        } catch (Exception e) {
            showStatus("Error: " + e.getMessage(), true);
        }
    }

    @FXML
    public void handleDelete() {
        if (selectedItem == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + selectedItem.getName() + "'? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                menuService.deleteMenuItem(selectedItem.getId());
                handleClear();
                loadMenuItems();
                showStatus("Item deleted.", false);
            }
        });
    }

    @FXML
    public void handleClear() {
        selectedItem = null;
        nameField.clear();
        categoryField.clear();
        priceField.clear();
        descriptionArea.clear();
        availableCheck.setSelected(true);
        menuTable.getSelectionModel().clearSelection();
        deleteButton.setDisable(true);
        statusLabel.setText("");
    }

    @FXML
    public void handleBack() throws IOException {
        stageManager.showDashboard();
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill:#e74c3c;" : "-fx-text-fill:#27ae60;");
    }
}
