package com.hotel.ui;

import com.hotel.delivery.entity.MenuPlatformMapping;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.service.MenuSyncService;
import com.hotel.entity.MenuItem;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuSyncController {

    private final MenuSyncService menuSyncService;
    private final StageManager    stageManager;

    @FXML private ComboBox<PlatformType>              platformCombo;
    @FXML private TableView<MenuItem>                 unmappedTable;
    @FXML private TableColumn<MenuItem, String>       colUnmappedName;
    @FXML private TableColumn<MenuItem, String>       colUnmappedCategory;
    @FXML private TableColumn<MenuItem, String>       colUnmappedPrice;
    @FXML private TableView<MenuPlatformMapping>      mappedTable;
    @FXML private TableColumn<MenuPlatformMapping, String> colMappedName;
    @FXML private TableColumn<MenuPlatformMapping, String> colMappedPlatformId;
    @FXML private TableColumn<MenuPlatformMapping, String> colMappedLastSync;
    @FXML private TextField  platformItemIdField;
    @FXML private TextField  platformCategoryIdField;
    @FXML private Label      statusLabel;
    @FXML private TextArea   syncLogArea;

    @FXML
    public void initialize() {
        platformCombo.setItems(FXCollections.observableArrayList(
                PlatformType.ZOMATO, PlatformType.SWIGGY, PlatformType.MOCK));
        platformCombo.setValue(PlatformType.MOCK);
        platformCombo.setOnAction(e -> loadData());
        setupTables();
        loadData();
    }

    @FXML
    public void handleSaveMapping() {
        MenuItem selected = unmappedTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select an item to map", false); return; }
        String platformItemId = platformItemIdField.getText().trim();
        if (platformItemId.isBlank()) { showStatus("Enter a Platform Item ID", false); return; }

        PlatformType platform = platformCombo.getValue();
        menuSyncService.saveMapping(selected.getId(), platform, platformItemId,
                platformCategoryIdField.getText().trim());
        appendLog("Mapped '" + selected.getName() + "' → " + platformItemId);
        showStatus("Mapping saved", true);
        loadData();
    }

    @FXML
    public void handleSyncAll() {
        PlatformType platform = platformCombo.getValue();
        if (platform == null) { showStatus("Select a platform first", false); return; }
        try {
            String result = menuSyncService.syncFullMenuToPlatform(platform);
            appendLog(result);
            showStatus(result, true);
            loadData();
        } catch (Exception e) {
            showStatus("Sync failed: " + e.getMessage(), false);
            appendLog("ERROR: " + e.getMessage());
        }
    }

    @FXML
    public void handleRefresh() {
        loadData();
    }

    @FXML
    public void handleBack() throws IOException {
        stageManager.showOnlineOrdersDashboard();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void setupTables() {
        colUnmappedName    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colUnmappedCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        colUnmappedPrice   .setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getPrice()));

        colMappedName      .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMenuItem().getName()));
        colMappedPlatformId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPlatformItemId()));
        colMappedLastSync  .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLastSyncedAt() != null ? c.getValue().getLastSyncedAt().toString() : "Never"));

        unmappedTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    if (sel != null) platformItemIdField.setPromptText("Enter ID for: " + sel.getName());
                });
    }

    private void loadData() {
        PlatformType platform = platformCombo.getValue();
        if (platform == null) return;

        List<MenuItem> unmapped = menuSyncService.getUnmappedItems(platform);
        unmappedTable.setItems(FXCollections.observableArrayList(unmapped));

        List<MenuPlatformMapping> mapped = menuSyncService.getMappingsForPlatform(platform);
        mappedTable.setItems(FXCollections.observableArrayList(mapped));
    }

    private void showStatus(String msg, boolean success) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + (success ? "#27ae60" : "#e74c3c") + ";-fx-font-weight:bold;");
    }

    private void appendLog(String line) {
        if (syncLogArea != null)
            syncLogArea.appendText("\n" + java.time.LocalTime.now().toString().substring(0, 8) + "  " + line);
    }
}
