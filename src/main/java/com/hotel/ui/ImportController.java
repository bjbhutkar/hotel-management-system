package com.hotel.ui;

import com.hotel.dto.ImportResult;
import com.hotel.service.ExcelImportService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportController {

    private final ExcelImportService excelImportService;
    private final StageManager       stageManager;

    // ── Menu Items tab ────────────────────────────────────────────────────────
    @FXML private Label         menuFileLabel;
    @FXML private TableView<List<String>>             menuPreviewTable;
    @FXML private Button        menuImportButton;
    @FXML private ProgressBar   menuProgressBar;
    @FXML private TextArea      menuResultArea;

    // ── Orders tab ────────────────────────────────────────────────────────────
    @FXML private Label         ordersFileLabel;
    @FXML private TableView<List<String>>             ordersPreviewTable;
    @FXML private Button        ordersImportButton;
    @FXML private ProgressBar   ordersProgressBar;
    @FXML private TextArea      ordersResultArea;

    private File selectedMenuFile;
    private File selectedOrdersFile;

    @FXML
    public void initialize() {
        menuProgressBar  .setVisible(false);
        ordersProgressBar.setVisible(false);
        menuImportButton  .setDisable(true);
        ordersImportButton.setDisable(true);
    }

    // ── Menu Items tab actions ────────────────────────────────────────────────

    @FXML
    public void handleChooseMenuFile() {
        File file = chooseExcelFile();
        if (file == null) return;
        selectedMenuFile = file;
        menuFileLabel.setText(file.getName());
        loadPreview(file, menuPreviewTable,
                new String[]{"Name", "Category", "Price", "Description", "Available"});
        menuImportButton.setDisable(false);
        menuResultArea.clear();
    }

    @FXML
    public void handleImportMenu() {
        if (selectedMenuFile == null) return;
        runImportTask(
                () -> excelImportService.importMenuItems(selectedMenuFile),
                menuProgressBar, menuResultArea, menuImportButton);
    }

    @FXML
    public void handleDownloadMenuTemplate() {
        saveTemplate("menu-items-template.xlsx",
                dest -> excelImportService.generateMenuItemTemplate(dest));
    }

    // ── Orders tab actions ────────────────────────────────────────────────────

    @FXML
    public void handleChooseOrdersFile() {
        File file = chooseExcelFile();
        if (file == null) return;
        selectedOrdersFile = file;
        ordersFileLabel.setText(file.getName());
        loadPreview(file, ordersPreviewTable,
                new String[]{"Order #", "Table", "Customer", "Date",
                             "Item Name", "Qty", "Unit Price", "Tax %", "Payment"});
        ordersImportButton.setDisable(false);
        ordersResultArea.clear();
    }

    @FXML
    public void handleImportOrders() {
        if (selectedOrdersFile == null) return;
        runImportTask(
                () -> excelImportService.importOrders(selectedOrdersFile),
                ordersProgressBar, ordersResultArea, ordersImportButton);
    }

    @FXML
    public void handleDownloadOrderTemplate() {
        saveTemplate("order-history-template.xlsx",
                dest -> excelImportService.generateOrderTemplate(dest));
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML
    public void handleBack() throws IOException {
        stageManager.showDashboard();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private File chooseExcelFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Excel File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"));
        Window window = menuFileLabel.getScene() != null
                ? menuFileLabel.getScene().getWindow() : null;
        return chooser.showOpenDialog(window);
    }

    private void loadPreview(File file, TableView<List<String>> table, String[] headers) {
        table.getColumns().clear();
        table.getItems().clear();

        for (int i = 0; i < headers.length; i++) {
            final int col = i;
            TableColumn<List<String>, String> tc = new TableColumn<>(headers[col]);
            tc.setCellValueFactory(cd -> {
                List<String> row = cd.getValue();
                return new SimpleStringProperty(col < row.size() ? row.get(col) : "");
            });
            tc.setPrefWidth(120);
            table.getColumns().add(tc);
        }

        List<List<String>> rows = new ArrayList<>();
        try (InputStream is = Files.newInputStream(file.toPath());
             var wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            int limit = Math.min(sheet.getLastRowNum(), 50); // preview first 50 rows
            for (int r = 1; r <= limit; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> rowData = new ArrayList<>();
                for (int c = 0; c < headers.length; c++) {
                    var cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    rowData.add(cell == null ? "" : cell.toString());
                }
                rows.add(rowData);
            }
        } catch (Exception e) {
            log.warn("Could not load preview: {}", e.getMessage());
        }
        table.setItems(FXCollections.observableArrayList(rows));
    }

    @FunctionalInterface
    private interface ImportTask { ImportResult run() throws IOException; }

    @FunctionalInterface
    private interface TemplateWriter { void write(File dest) throws IOException; }

    private void runImportTask(ImportTask task, ProgressBar progress,
                                TextArea resultArea, Button importBtn) {
        progress.setVisible(true);
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        importBtn.setDisable(true);

        Task<ImportResult> bgTask = new Task<>() {
            @Override
            protected ImportResult call() throws Exception {
                return task.run();
            }
        };

        bgTask.setOnSucceeded(e -> Platform.runLater(() -> {
            ImportResult result = bgTask.getValue();
            progress.setVisible(false);
            importBtn.setDisable(false);
            displayResult(result, resultArea);
        }));

        bgTask.setOnFailed(e -> Platform.runLater(() -> {
            progress.setVisible(false);
            importBtn.setDisable(false);
            resultArea.setText("Import failed: " + bgTask.getException().getMessage());
            log.error("Import task failed", bgTask.getException());
        }));

        Thread thread = new Thread(bgTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void displayResult(ImportResult result, TextArea area) {
        StringBuilder sb = new StringBuilder();
        sb.append("── Import Complete ──────────────────────────\n");
        sb.append(result.getSummary()).append("\n\n");

        if (!result.getWarnings().isEmpty()) {
            sb.append("── Warnings ─────────────────────────────────\n");
            result.getWarnings().forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
            sb.append("\n");
        }
        if (!result.getErrors().isEmpty()) {
            sb.append("── Errors ───────────────────────────────────\n");
            result.getErrors().forEach(err -> sb.append("  ✘ ").append(err).append("\n"));
        }
        if (result.isFullSuccess()) {
            sb.append("✔ All rows imported successfully!");
        }
        area.setText(sb.toString());
    }

    private void saveTemplate(String defaultName, TemplateWriter writer) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Sample Template");
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        Window window = menuFileLabel.getScene() != null
                ? menuFileLabel.getScene().getWindow() : null;
        File dest = chooser.showSaveDialog(window);
        if (dest == null) return;
        try {
            writer.write(dest);
            showInfo("Template saved to " + dest.getName());
        } catch (IOException e) {
            showError("Could not save template: " + e.getMessage());
        }
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Error");
        a.showAndWait();
    }
}
