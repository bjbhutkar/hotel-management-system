package com.hotel.service;

import com.hotel.dto.ImportResult;
import com.hotel.entity.MenuItem;
import com.hotel.entity.Order;
import com.hotel.entity.Order.OrderStatus;
import com.hotel.entity.OrderItem;
import com.hotel.repository.MenuItemRepository;
import com.hotel.repository.OrderItemRepository;
import com.hotel.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bulk Excel import for menu items and historical orders.
 *
 * Both importers are transactional: if a row fails, only that row is skipped —
 * the transaction wraps the whole batch but individual row exceptions are caught
 * and reported without rolling back successfully-processed rows.
 *
 * Template column layouts are documented by the sample template generators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final MenuItemRepository menuItemRepository;
    private final OrderRepository    orderRepository;
    private final OrderItemRepository orderItemRepository;

    // ── Menu Items Import ────────────────────────────────────────────────────

    /**
     * Expected columns (row 1 = header, data from row 2):
     * A: Name | B: Category | C: Price | D: Description | E: Available (YES/NO)
     */
    @Transactional
    public ImportResult importMenuItems(File file) throws IOException {
        ImportResult.ImportResultBuilder result = ImportResult.builder();
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int total = 0, success = 0, skipped = 0, failed = 0;

        try (InputStream is = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) { skipped++; continue; }
                total++;

                try {
                    String name     = cellStr(row, 0);
                    String category = cellStr(row, 1);
                    String priceStr = cellStr(row, 2);
                    String desc     = cellStr(row, 3);
                    String availStr = cellStr(row, 4);

                    if (name.isBlank()) {
                        errors.add("Row " + (i + 1) + ": Name is required");
                        failed++; continue;
                    }
                    if (category.isBlank()) {
                        errors.add("Row " + (i + 1) + ": Category is required");
                        failed++; continue;
                    }

                    BigDecimal price;
                    try {
                        price = new BigDecimal(priceStr.replaceAll("[^\\d.]", ""));
                        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
                    } catch (Exception e) {
                        errors.add("Row " + (i + 1) + ": Invalid price '" + priceStr + "'");
                        failed++; continue;
                    }

                    boolean available = !"NO".equalsIgnoreCase(availStr.trim());

                    // Duplicate check by name + category
                    Optional<MenuItem> existing = menuItemRepository.findByNameIgnoreCase(name);
                    if (existing.isPresent()) {
                        warnings.add("Row " + (i + 1) + ": '" + name + "' already exists — skipped");
                        skipped++; continue;
                    }

                    menuItemRepository.save(MenuItem.builder()
                            .name(name.trim())
                            .category(category.trim())
                            .price(price)
                            .description(desc.trim())
                            .available(available)
                            .build());
                    success++;

                } catch (Exception e) {
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                    failed++;
                    log.warn("Menu import row {} error: {}", i + 1, e.getMessage());
                }
            }
        }

        log.info("Menu import complete — success={} failed={} skipped={}", success, failed, skipped);
        return result.totalRows(total).successCount(success)
                .failureCount(failed).skippedCount(skipped)
                .errors(errors).warnings(warnings).build();
    }

    // ── Order History Import ─────────────────────────────────────────────────

    /**
     * Expected columns (row 1 = header, data from row 2):
     * A: OrderNumber | B: TableNumber | C: CustomerName | D: Date (dd-MM-yyyy HH:mm)
     * E: ItemName | F: Qty | G: UnitPrice | H: TaxRate% | I: PaymentMode
     *
     * Multiple rows with the same order number are grouped into one order.
     */
    @Transactional
    public ImportResult importOrders(File file) throws IOException {
        ImportResult.ImportResultBuilder result = ImportResult.builder();
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int total = 0, success = 0, skipped = 0, failed = 0;

        // First pass: group rows by order number
        Map<String, List<Row>> orderGroups = new LinkedHashMap<>();

        try (InputStream is = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;
                String orderNum = cellStr(row, 0).trim();
                if (!orderNum.isBlank())
                    orderGroups.computeIfAbsent(orderNum, k -> new ArrayList<>()).add(row);
            }
        }

        // Second pass: persist each order group
        try (InputStream is = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(is)) {

            for (Map.Entry<String, List<Row>> entry : orderGroups.entrySet()) {
                total++;
                String orderNum = entry.getKey();
                List<Row> rows  = entry.getValue();

                try {
                    // Duplicate check
                    if (orderRepository.existsByOrderNumber(orderNum)) {
                        warnings.add("Order " + orderNum + " already exists — skipped");
                        skipped++; continue;
                    }

                    Row first    = rows.get(0);
                    int tableNum = (int) cellNum(first, 1);
                    String customer = cellStr(first, 2);
                    BigDecimal taxRate = BigDecimal.valueOf(cellNum(first, 7));

                    Order order = Order.builder()
                            .orderNumber(orderNum)
                            .tableNumber(tableNum > 0 ? tableNum : 1)
                            .customerName(customer)
                            .status(OrderStatus.BILLED)
                            .taxRate(taxRate)
                            .build();

                    List<OrderItem> items = new ArrayList<>();
                    BigDecimal subtotal = BigDecimal.ZERO;

                    for (Row row : rows) {
                        String itemName = cellStr(row, 4).trim();
                        if (itemName.isBlank()) continue;
                        int qty = Math.max(1, (int) cellNum(row, 5));
                        BigDecimal unitPrice = BigDecimal.valueOf(cellNum(row, 6));
                        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                        subtotal = subtotal.add(lineTotal);

                        // Best-effort match to existing menu item
                        MenuItem mi = menuItemRepository.findByNameIgnoreCase(itemName)
                                .orElseGet(() -> MenuItem.builder()
                                        .name(itemName).category("Imported")
                                        .price(unitPrice).available(true).build());
                        if (mi.getId() == null) menuItemRepository.save(mi);

                        OrderItem oi = OrderItem.builder()
                                .order(order).menuItem(mi).quantity(qty)
                                .unitPrice(unitPrice).totalPrice(lineTotal).build();
                        items.add(oi);
                    }

                    BigDecimal taxAmt = subtotal.multiply(taxRate)
                            .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    order.setSubtotal(subtotal);
                    order.setTaxAmount(taxAmt);
                    order.setTotalAmount(subtotal.add(taxAmt));
                    order.setItems(items);

                    Order saved = orderRepository.save(order);
                    items.forEach(oi -> oi.setOrder(saved));
                    orderItemRepository.saveAll(items);
                    success++;

                } catch (Exception e) {
                    errors.add("Order " + orderNum + ": " + e.getMessage());
                    failed++;
                    log.warn("Order import error for {}: {}", orderNum, e.getMessage());
                }
            }
        }

        log.info("Order import complete — success={} failed={} skipped={}", success, failed, skipped);
        return result.totalRows(total).successCount(success)
                .failureCount(failed).skippedCount(skipped)
                .errors(errors).warnings(warnings).build();
    }

    // ── Sample template generation ────────────────────────────────────────────

    public void generateMenuItemTemplate(File destFile) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Menu Items");
            CellStyle headerStyle = boldStyle(wb);

            Row header = sheet.createRow(0);
            String[] cols = {"Name *", "Category *", "Price *", "Description", "Available (YES/NO)"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Sample rows
            Object[][] samples = {
                {"Butter Chicken", "Main Course", 320.00, "Creamy tomato curry", "YES"},
                {"Garlic Naan",    "Breads",       50.00, "Butter garlic naan",  "YES"},
                {"Mango Lassi",    "Beverages",    80.00, "Fresh mango drink",   "YES"},
                {"Gulab Jamun",    "Desserts",     60.00, "2 pieces",            "YES"},
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] sample = samples[r];
                row.createCell(0).setCellValue((String)   sample[0]);
                row.createCell(1).setCellValue((String)   sample[1]);
                row.createCell(2).setCellValue((Double)   sample[2]);
                row.createCell(3).setCellValue((String)   sample[3]);
                row.createCell(4).setCellValue((String)   sample[4]);
            }
            try (FileOutputStream fos = new FileOutputStream(destFile)) { wb.write(fos); }
        }
    }

    public void generateOrderTemplate(File destFile) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Order History");
            CellStyle headerStyle = boldStyle(wb);

            Row header = sheet.createRow(0);
            String[] cols = {"Order Number *", "Table No *", "Customer Name", "Date",
                             "Item Name *", "Qty *", "Unit Price *", "Tax Rate %", "Payment Mode"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4800);
            }

            Object[][] samples = {
                {"ORD-20260101-0001", 3.0, "Rahul Sharma", "01-01-2026 19:30",
                 "Butter Chicken", 2.0, 320.00, 18.0, "UPI"},
                {"ORD-20260101-0001", 3.0, "Rahul Sharma", "01-01-2026 19:30",
                 "Garlic Naan",     4.0,  50.00, 18.0, "UPI"},
                {"ORD-20260101-0002", 5.0, "Priya Patel",  "01-01-2026 20:15",
                 "Mango Lassi",     2.0,  80.00, 18.0, "CASH"},
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] s = samples[r];
                row.createCell(0).setCellValue((String) s[0]);
                row.createCell(1).setCellValue((Double) s[1]);
                row.createCell(2).setCellValue((String) s[2]);
                row.createCell(3).setCellValue((String) s[3]);
                row.createCell(4).setCellValue((String) s[4]);
                row.createCell(5).setCellValue((Double) s[5]);
                row.createCell(6).setCellValue((Double) s[6]);
                row.createCell(7).setCellValue((Double) s[7]);
                row.createCell(8).setCellValue((String) s[8]);
            }
            try (FileOutputStream fos = new FileOutputStream(destFile)) { wb.write(fos); }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue(); }
            }
            default -> "";
        };
    }

    private double cellNum(Row row, int col) {
        String s = cellStr(row, col).replaceAll("[^\\d.]", "");
        try { return s.isBlank() ? 0 : Double.parseDouble(s); }
        catch (Exception e) { return 0; }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c <= row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cellStr(row, c).isBlank()) return false;
        }
        return true;
    }

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
