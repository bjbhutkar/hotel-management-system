package com.hotel.ui;

import com.hotel.entity.Order;
import com.hotel.service.ReportService;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReportController {

    @FXML private TabPane reportTabs;

    /* Daily */
    @FXML private DatePicker dailyDatePicker;
    @FXML private TextArea   dailyReportArea;

    /* Weekly */
    @FXML private DatePicker weeklyDatePicker;
    @FXML private TextArea   weeklyReportArea;

    /* Monthly */
    @FXML private ComboBox<String>  monthCombo;
    @FXML private ComboBox<Integer> yearCombo;
    @FXML private TextArea          monthlyReportArea;

    /* Cancelled */
    @FXML private DatePicker cancelledFromPicker;
    @FXML private DatePicker cancelledToPicker;
    @FXML private TextArea   cancelledReportArea;

    /* Item Analytics */
    @FXML private DatePicker                    itemsFromPicker;
    @FXML private DatePicker                    itemsToPicker;
    @FXML private TableView<Object[]>           topItemsTable;
    @FXML private TableColumn<Object[], String> topItemNameCol;
    @FXML private TableColumn<Object[], String> topItemQtyCol;
    @FXML private TableView<Object[]>           leastItemsTable;
    @FXML private TableColumn<Object[], String> leastItemNameCol;
    @FXML private TableColumn<Object[], String> leastItemQtyCol;

    /* Revenue Chart */
    @FXML private DatePicker              chartFromPicker;
    @FXML private DatePicker              chartToPicker;
    @FXML private BarChart<String, Number> revenueChart;

    private final ReportService reportService;
    private final StageManager  stageManager;

    // Cached last-generated data for export
    private Map<String, Object> lastDailyReport;
    private Map<String, Object> lastWeeklyReport;
    private Map<String, Object> lastMonthlyReport;
    private List<Order>         lastCancelledOrders;
    private LocalDate           lastCancelledFrom, lastCancelledTo;
    private List<Object[]>      lastTopItems;
    private List<Object[]>      lastLeastItems;
    private LocalDate           lastItemsFrom, lastItemsTo;

    @FXML
    public void initialize() {
        dailyDatePicker.setValue(LocalDate.now());
        weeklyDatePicker.setValue(LocalDate.now().with(DayOfWeek.MONDAY));

        monthCombo.getItems().addAll(
                "January","February","March","April","May","June",
                "July","August","September","October","November","December");
        monthCombo.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);

        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear; y >= currentYear - 5; y--) yearCombo.getItems().add(y);
        yearCombo.getSelectionModel().selectFirst();

        cancelledFromPicker.setValue(LocalDate.now().withDayOfMonth(1));
        cancelledToPicker.setValue(LocalDate.now());

        itemsFromPicker.setValue(LocalDate.now().withDayOfMonth(1));
        itemsToPicker.setValue(LocalDate.now());

        chartFromPicker.setValue(LocalDate.now().minusDays(6));
        chartToPicker.setValue(LocalDate.now());

        topItemNameCol.setCellValueFactory(d -> new SimpleStringProperty((String) d.getValue()[0]));
        topItemQtyCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue()[1])));
        leastItemNameCol.setCellValueFactory(d -> new SimpleStringProperty((String) d.getValue()[0]));
        leastItemQtyCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue()[1])));

        ((CategoryAxis) revenueChart.getXAxis()).setTickLabelRotation(45);
    }

    // ── Report generation ─────────────────────────────────────────────────

    @FXML
    public void generateDailyReport() {
        LocalDate date = dailyDatePicker.getValue();
        if (date == null) { dailyReportArea.setText("Please select a date."); return; }
        lastDailyReport = reportService.getDailyReport(date);
        dailyReportArea.setText(formatReport(lastDailyReport));
    }

    @FXML
    public void generateWeeklyReport() {
        LocalDate weekStart = weeklyDatePicker.getValue();
        if (weekStart == null) { weeklyReportArea.setText("Please select a week start date."); return; }
        lastWeeklyReport = reportService.getWeeklyReport(weekStart);
        weeklyReportArea.setText(formatReport(lastWeeklyReport));
    }

    @FXML
    public void generateMonthlyReport() {
        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int year  = yearCombo.getSelectionModel().getSelectedItem();
        lastMonthlyReport = reportService.getMonthlyReport(year, month);
        monthlyReportArea.setText(formatReport(lastMonthlyReport));
    }

    @FXML
    public void generateCancelledReport() {
        LocalDate from = cancelledFromPicker.getValue();
        LocalDate to   = cancelledToPicker.getValue();
        if (from == null || to == null) { cancelledReportArea.setText("Select date range."); return; }
        lastCancelledFrom   = from;
        lastCancelledTo     = to;
        lastCancelledOrders = reportService.getCancelledOrders(
                from.atStartOfDay(), to.atTime(LocalTime.MAX));

        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(56)).append("\n");
        sb.append("          CANCELLED ORDERS REPORT\n");
        sb.append("=".repeat(56)).append("\n");
        sb.append(String.format("Period       : %s  to  %s%n", from, to));
        sb.append(String.format("Total Records: %d%n%n", lastCancelledOrders.size()));
        lastCancelledOrders.forEach(o -> {
            sb.append(String.format("Order    : %s%n", o.getOrderNumber()));
            sb.append(String.format("Table    : %d%n",  o.getTableNumber()));
            sb.append(String.format("Amount   : ₹%.2f%n", o.getTotalAmount()));
            sb.append(String.format("Reason   : %s%n",
                    o.getCancellationReason() != null ? o.getCancellationReason() : "N/A"));
            sb.append(String.format("Date     : %s%n", o.getCreatedAt()));
            sb.append("-".repeat(56)).append("\n");
        });
        cancelledReportArea.setText(sb.toString());
    }

    @FXML
    public void generateItemsReport() {
        LocalDate from = itemsFromPicker.getValue();
        LocalDate to   = itemsToPicker.getValue();
        if (from == null || to == null || from.isAfter(to)) {
            showAlert("Select a valid date range.");
            return;
        }
        lastItemsFrom  = from;
        lastItemsTo    = to;
        lastTopItems   = reportService.getMostSoldItems(from.atStartOfDay(), to.atTime(LocalTime.MAX));
        lastLeastItems = reportService.getLeastSoldItems(from.atStartOfDay(), to.atTime(LocalTime.MAX));
        topItemsTable.setItems(FXCollections.observableArrayList(lastTopItems));
        leastItemsTable.setItems(FXCollections.observableArrayList(lastLeastItems));
    }

    @FXML
    public void generateRevenueChart() {
        LocalDate from = chartFromPicker.getValue();
        LocalDate to   = chartToPicker.getValue();
        if (from == null || to == null || from.isAfter(to)) {
            showAlert("Select a valid date range.");
            return;
        }
        Map<LocalDate, BigDecimal> daily = reportService.getDailyRevenue(from, to);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue (₹)");
        daily.forEach((date, revenue) ->
                series.getData().add(new XYChart.Data<>(date.toString(), revenue)));
        revenueChart.getData().clear();
        revenueChart.getData().add(series);
    }

    // ── PDF exports ───────────────────────────────────────────────────────

    @FXML public void exportDailyPdf()     { exportTextToPdf(dailyReportArea.getText(),     "daily-report");     }
    @FXML public void exportWeeklyPdf()    { exportTextToPdf(weeklyReportArea.getText(),    "weekly-report");    }
    @FXML public void exportMonthlyPdf()   { exportTextToPdf(monthlyReportArea.getText(),   "monthly-report");   }
    @FXML public void exportCancelledPdf() { exportTextToPdf(cancelledReportArea.getText(), "cancelled-report"); }

    @FXML
    public void exportItemsPdf() {
        if (lastTopItems == null) { showAlert("Generate the Items report first."); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("ITEM ANALYTICS REPORT\n");
        sb.append("Period: ").append(lastItemsFrom).append(" to ").append(lastItemsTo).append("\n\n");
        sb.append("TOP SELLING ITEMS:\n").append("-".repeat(40)).append("\n");
        lastTopItems.forEach(r -> sb.append(String.format("  %-30s : %s units%n", r[0], r[1])));
        sb.append("\nLEAST SELLING ITEMS:\n").append("-".repeat(40)).append("\n");
        lastLeastItems.forEach(r -> sb.append(String.format("  %-30s : %s units%n", r[0], r[1])));
        exportTextToPdf(sb.toString(), "items-report");
    }

    // ── Excel exports ─────────────────────────────────────────────────────

    @FXML public void exportDailyExcel()   { exportReportToExcel(lastDailyReport,   "daily-report");   }
    @FXML public void exportWeeklyExcel()  { exportReportToExcel(lastWeeklyReport,  "weekly-report");  }
    @FXML public void exportMonthlyExcel() { exportReportToExcel(lastMonthlyReport, "monthly-report"); }

    @FXML
    public void exportCancelledExcel() {
        if (lastCancelledOrders == null) { showAlert("Generate the Cancelled Orders report first."); return; }
        File file = buildChooser("xlsx", "cancelled-report").showSaveDialog(getStage());
        if (file == null) return;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Cancelled Orders");
            createHeaderRow(sheet, "Order #", "Table", "Amount (₹)", "Reason", "Date");
            int r = 1;
            for (Order o : lastCancelledOrders) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(o.getOrderNumber());
                row.createCell(1).setCellValue(o.getTableNumber());
                row.createCell(2).setCellValue(o.getTotalAmount().doubleValue());
                row.createCell(3).setCellValue(o.getCancellationReason() != null ? o.getCancellationReason() : "");
                row.createCell(4).setCellValue(o.getCreatedAt().toString());
            }
            writeWorkbook(wb, file);
        } catch (Exception e) {
            showAlert("Excel export failed: " + e.getMessage());
        }
    }

    @FXML
    public void exportItemsExcel() {
        if (lastTopItems == null) { showAlert("Generate the Items report first."); return; }
        File file = buildChooser("xlsx", "items-report").showSaveDialog(getStage());
        if (file == null) return;
        try (Workbook wb = new XSSFWorkbook()) {
            addItemSheet(wb, "Top Selling Items",   lastTopItems);
            addItemSheet(wb, "Least Selling Items", lastLeastItems);
            writeWorkbook(wb, file);
        } catch (Exception e) {
            showAlert("Excel export failed: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void exportTextToPdf(String content, String name) {
        if (content == null || content.isBlank()) { showAlert("Generate the report first."); return; }
        File file = buildChooser("pdf", name).showSaveDialog(getStage());
        if (file == null) return;
        try {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, new FileOutputStream(file));
            doc.open();
            Font font = new Font(Font.COURIER, 9f, Font.NORMAL);
            for (String line : content.replace("\r\n", "\n").split("\n")) {
                Paragraph para = new Paragraph(line.isEmpty() ? " " : line, font);
                para.setLeading(11f);
                para.setSpacingBefore(0f);
                para.setSpacingAfter(0f);
                doc.add(para);
            }
            doc.close();
        } catch (Exception e) {
            showAlert("PDF export failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void exportReportToExcel(Map<String, Object> report, String name) {
        if (report == null) { showAlert("Generate the report first."); return; }
        File file = buildChooser("xlsx", name).showSaveDialog(getStage());
        if (file == null) return;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet summary = wb.createSheet("Summary");
            createHeaderRow(summary, "Metric", "Value");
            String[][] stats = {
                {"Total Orders",         String.valueOf(report.get("total_orders"))},
                {"Billed Orders",        String.valueOf(report.get("billed_orders"))},
                {"Cancelled Orders",     String.valueOf(report.get("cancelled_orders"))},
                {"Subtotal (excl. GST)", "₹" + report.get("subtotal")},
                {"GST Collected",        "₹" + report.get("tax_collected")},
                {"Total Revenue",        "₹" + report.get("total_revenue")},
            };
            for (int i = 0; i < stats.length; i++) {
                Row row = summary.createRow(i + 1);
                row.createCell(0).setCellValue(stats[i][0]);
                row.createCell(1).setCellValue(stats[i][1]);
            }
            addItemSheet(wb, "Top Selling Items",   (List<Object[]>) report.get("most_sold_items"));
            addItemSheet(wb, "Least Selling Items",  (List<Object[]>) report.get("least_sold_items"));
            writeWorkbook(wb, file);
        } catch (Exception e) {
            showAlert("Excel export failed: " + e.getMessage());
        }
    }

    private void addItemSheet(Workbook wb, String sheetName, List<Object[]> items) {
        if (items == null || items.isEmpty()) return;
        Sheet sheet = wb.createSheet(sheetName);
        createHeaderRow(sheet, "Item", "Units Sold");
        int r = 1;
        for (Object[] item : items) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue((String) item[0]);
            row.createCell(1).setCellValue(((Number) item[1]).longValue());
        }
    }

    private void createHeaderRow(Sheet sheet, String... headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    private void writeWorkbook(Workbook wb, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
        }
    }

    private FileChooser buildChooser(String ext, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save File");
        if ("pdf".equals(ext)) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        } else {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        }
        chooser.setInitialFileName(suggestedName + "." + ext);
        return chooser;
    }

    private Stage getStage() {
        return (Stage) reportTabs.getScene().getWindow();
    }

    private void showAlert(String message) {
        new Alert(Alert.AlertType.WARNING, message, ButtonType.OK).showAndWait();
    }

    @FXML
    public void handleBack() throws IOException {
        stageManager.showDashboard();
    }

    @SuppressWarnings("unchecked")
    private String formatReport(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder();
        String eq = "=".repeat(56);
        sb.append(eq).append("\n");
        sb.append("  ").append(r.get("title")).append("\n");
        sb.append(eq).append("\n\n");
        sb.append(String.format("%-28s : %s%n",  "Total Orders",     r.get("total_orders")));
        sb.append(String.format("%-28s : %s%n",  "Billed Orders",    r.get("billed_orders")));
        sb.append(String.format("%-28s : %s%n",  "Cancelled Orders", r.get("cancelled_orders")));
        sb.append("\n");
        sb.append(String.format("%-28s : ₹%s%n", "Subtotal (excl. GST)", r.get("subtotal")));
        sb.append(String.format("%-28s : ₹%s%n", "GST Collected",        r.get("tax_collected")));
        sb.append(String.format("%-28s : ₹%s%n", "Total Revenue",        r.get("total_revenue")));
        sb.append("\n");
        List<Object[]> most = (List<Object[]>) r.get("most_sold_items");
        if (most != null && !most.isEmpty()) {
            sb.append("TOP SELLING ITEMS:\n").append("-".repeat(40)).append("\n");
            most.forEach(row -> sb.append(String.format("  %-30s : %s units%n", row[0], row[1])));
            sb.append("\n");
        }
        List<Object[]> least = (List<Object[]>) r.get("least_sold_items");
        if (least != null && !least.isEmpty()) {
            sb.append("LEAST SELLING ITEMS:\n").append("-".repeat(40)).append("\n");
            least.forEach(row -> sb.append(String.format("  %-30s : %s units%n", row[0], row[1])));
        }
        sb.append("\n").append(eq);
        return sb.toString();
    }
}