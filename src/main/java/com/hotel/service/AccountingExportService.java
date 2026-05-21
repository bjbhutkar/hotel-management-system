package com.hotel.service;

import com.hotel.entity.Order;
import com.hotel.entity.OrderItem;
import com.hotel.entity.RestaurantConfig;
import com.hotel.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingExportService {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter TALLY_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final RestaurantConfigService restaurantConfigService;

    public List<Order> getBilledOrders(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        return orderRepository.findByStatusAndCreatedAtBetween(Order.OrderStatus.BILLED, start, end);
    }

    // ── Excel Export ──────────────────────────────────────────────────────────

    public void exportToExcel(List<Order> orders, LocalDate from, LocalDate to, File outputFile) throws IOException {
        RestaurantConfig cfg = restaurantConfigService.getConfig();
        try (Workbook wb = new XSSFWorkbook()) {
            buildSummarySheet(wb, orders, cfg, from, to);
            buildOrderDetailsSheet(wb, orders, cfg);
            buildItemWiseSheet(wb, orders);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                wb.write(fos);
            }
        }
        log.info("Accounting Excel exported: {}", outputFile.getAbsolutePath());
    }

    private void buildSummarySheet(Workbook wb, List<Order> orders, RestaurantConfig cfg,
                                   LocalDate from, LocalDate to) {
        Sheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 35 * 256);
        sheet.setColumnWidth(1, 22 * 256);

        CellStyle titleStyle  = makeStyle(wb, true, 14, IndexedColors.DARK_BLUE);
        CellStyle headerStyle = makeStyle(wb, true, 11, IndexedColors.DARK_TEAL);
        CellStyle labelStyle  = makeStyle(wb, false, 11, null);
        CellStyle amtStyle    = makeAmountStyle(wb);
        CellStyle countStyle  = makeStyle(wb, false, 11, null);

        int r = 0;
        setCell(sheet, r++, 0, cfg.getRestaurantName(), titleStyle);
        if (notBlank(cfg.getGstNumber()))
            setCell(sheet, r++, 0, "GSTIN: " + cfg.getGstNumber(), labelStyle);
        if (notBlank(cfg.getAddress()))
            setCell(sheet, r++, 0, cfg.getAddress(), labelStyle);
        r++;
        setCell(sheet, r++, 0,
                "Period: " + from.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                        + " to " + to.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), labelStyle);
        r++;

        BigDecimal subtotalSum = orders.stream().map(Order::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxSum      = orders.stream().map(Order::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSum    = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cgstSum     = taxSum.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal sgstSum     = taxSum.subtract(cgstSum);

        setCell(sheet, r, 0, "Metric", headerStyle);
        setCell(sheet, r++, 1, "Amount", headerStyle);

        setCell(sheet, r, 0, "Total Billed Orders", labelStyle);
        sheet.createRow(r).createCell(1).setCellValue(orders.size());
        sheet.getRow(r).getCell(1).setCellStyle(countStyle);
        r++;

        addAmountRow(sheet, r++, "Taxable Sales (Subtotal)", subtotalSum, labelStyle, amtStyle);
        addAmountRow(sheet, r++, "Output CGST", cgstSum, labelStyle, amtStyle);
        addAmountRow(sheet, r++, "Output SGST", sgstSum, labelStyle, amtStyle);
        addAmountRow(sheet, r++, "Total GST Collected", taxSum, labelStyle, amtStyle);
        addAmountRow(sheet, r,   "Total Revenue (Incl. GST)", totalSum, headerStyle, amtStyle);
    }

    private void buildOrderDetailsSheet(Workbook wb, List<Order> orders, RestaurantConfig cfg) {
        Sheet sheet = wb.createSheet("Order Details");
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 22 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        sheet.setColumnWidth(6, 14 * 256);
        sheet.setColumnWidth(7, 16 * 256);
        sheet.setColumnWidth(8, 16 * 256);
        sheet.setColumnWidth(9, 16 * 256);

        CellStyle hdr = makeStyle(wb, true, 11, IndexedColors.DARK_TEAL);
        CellStyle txt = makeStyle(wb, false, 11, null);
        CellStyle amt = makeAmountStyle(wb);

        String[] headers = {
            "Order Number", "Date & Time", "Customer Name", "Table No",
            "Items", "Subtotal (₹)", "Tax Rate (%)", "CGST (₹)", "SGST (₹)", "Total (₹)"
        };
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(hdr);
        }

        int r = 1;
        for (Order o : orders) {
            Row row = sheet.createRow(r++);
            BigDecimal cgst = o.getTaxAmount().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal sgst = o.getTaxAmount().subtract(cgst);

            setTextCell(row, 0, o.getOrderNumber(), txt);
            setTextCell(row, 1, o.getCreatedAt() != null ? o.getCreatedAt().format(DISPLAY_FMT) : "", txt);
            setTextCell(row, 2, o.getCustomerName() != null ? o.getCustomerName() : "Walk-in", txt);
            setNumCell(row, 3, o.getTableNumber() != null ? o.getTableNumber().doubleValue() : 0, txt);
            setNumCell(row, 4, o.getItems().size(), txt);
            setNumCell(row, 5, o.getSubtotal().doubleValue(), amt);
            setNumCell(row, 6, o.getTaxRate().doubleValue(), txt);
            setNumCell(row, 7, cgst.doubleValue(), amt);
            setNumCell(row, 8, sgst.doubleValue(), amt);
            setNumCell(row, 9, o.getTotalAmount().doubleValue(), amt);
        }
    }

    private void buildItemWiseSheet(Workbook wb, List<Order> orders) {
        Sheet sheet = wb.createSheet("Item-wise Sales");
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 20 * 256);

        CellStyle hdr = makeStyle(wb, true, 11, IndexedColors.DARK_TEAL);
        CellStyle txt = makeStyle(wb, false, 11, null);
        CellStyle amt = makeAmountStyle(wb);

        String[] headers = {"Item Name", "Category", "Total Qty Sold", "Total Revenue (₹)"};
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(hdr);
        }

        Map<String, long[]> aggregated = new LinkedHashMap<>();
        Map<String, String> itemCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> itemRevenue = new LinkedHashMap<>();

        for (Order o : orders) {
            for (OrderItem item : o.getItems()) {
                if (item.getMenuItem() == null) continue;
                String name = item.getMenuItem().getName();
                aggregated.computeIfAbsent(name, k -> new long[]{0})[0] += item.getQuantity();
                itemCategory.put(name, item.getMenuItem().getCategory());
                itemRevenue.merge(name, item.getTotalPrice(), BigDecimal::add);
            }
        }

        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(aggregated.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));

        int r = 1;
        for (Map.Entry<String, long[]> entry : sorted) {
            Row row = sheet.createRow(r++);
            setTextCell(row, 0, entry.getKey(), txt);
            setTextCell(row, 1, itemCategory.getOrDefault(entry.getKey(), ""), txt);
            setNumCell(row, 2, entry.getValue()[0], txt);
            setNumCell(row, 3, itemRevenue.getOrDefault(entry.getKey(), BigDecimal.ZERO).doubleValue(), amt);
        }
    }

    // ── Tally XML Export ──────────────────────────────────────────────────────

    public void exportToTallyXml(List<Order> orders, File outputFile) throws Exception {
        RestaurantConfig cfg = restaurantConfigService.getConfig();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element envelope = doc.createElement("ENVELOPE");
        doc.appendChild(envelope);

        // Header
        Element header = child(doc, envelope, "HEADER");
        child(doc, header, "TALLYREQUEST").setTextContent("Import Data");

        // Body
        Element body = child(doc, envelope, "BODY");
        Element importData = child(doc, body, "IMPORTDATA");

        Element requestDesc = child(doc, importData, "REQUESTDESC");
        child(doc, requestDesc, "REPORTNAME").setTextContent("Vouchers");
        Element staticVars = child(doc, requestDesc, "STATICVARIABLES");
        String companyName = notBlank(cfg.getRestaurantName()) ? cfg.getRestaurantName() : "Restaurant";
        child(doc, staticVars, "SVCURRENTCOMPANY").setTextContent(companyName);

        Element requestData = child(doc, importData, "REQUESTDATA");

        for (Order order : orders) {
            appendVoucher(doc, requestData, order);
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
        }
        log.info("Tally XML exported: {}", outputFile.getAbsolutePath());
    }

    private void appendVoucher(Document doc, Element requestData, Order order) {
        Element tallyMsg = child(doc, requestData, "TALLYMESSAGE");
        tallyMsg.setAttribute("xmlns:UDF", "TallyUDF");

        Element voucher = child(doc, tallyMsg, "VOUCHER");
        voucher.setAttribute("VCHTYPE", "Sales");
        voucher.setAttribute("ACTION", "Create");
        voucher.setAttribute("OBJVIEW", "Invoice Voucher View");

        String date = order.getCreatedAt() != null
                ? order.getCreatedAt().format(TALLY_DATE_FMT)
                : LocalDateTime.now().format(TALLY_DATE_FMT);
        child(doc, voucher, "DATE").setTextContent(date);
        child(doc, voucher, "VOUCHERTYPENAME").setTextContent("Sales");
        child(doc, voucher, "VOUCHERNUMBER").setTextContent(order.getOrderNumber());
        child(doc, voucher, "ISINVOICE").setTextContent("Yes");
        child(doc, voucher, "PARTYLEDGERNAME").setTextContent("Cash");

        String tableInfo = order.getTableNumber() != null ? " | Table " + order.getTableNumber() : "";
        String customerInfo = notBlank(order.getCustomerName()) ? " | " + order.getCustomerName() : "";
        child(doc, voucher, "NARRATION")
                .setTextContent("Restaurant Sale - " + order.getOrderNumber() + tableInfo + customerInfo);

        BigDecimal taxRate     = order.getTaxRate() != null ? order.getTaxRate() : BigDecimal.ZERO;
        BigDecimal halfRate    = taxRate.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal cgst        = order.getTaxAmount().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal sgst        = order.getTaxAmount().subtract(cgst);
        BigDecimal totalAmount = order.getTotalAmount();

        // DR: Cash (party ledger receives payment)
        appendLedgerEntry(doc, voucher, "Cash", "Yes",
                totalAmount.negate().toPlainString());

        // CR: Restaurant Sales
        appendLedgerEntry(doc, voucher, "Restaurant Sales", "No",
                order.getSubtotal().toPlainString());

        // CR: Output CGST (intra-state split)
        if (cgst.compareTo(BigDecimal.ZERO) > 0) {
            String cgstLedger = "Output CGST @ " + halfRate.stripTrailingZeros().toPlainString() + "%";
            appendLedgerEntry(doc, voucher, cgstLedger, "No", cgst.toPlainString());
        }

        // CR: Output SGST (intra-state split)
        if (sgst.compareTo(BigDecimal.ZERO) > 0) {
            String sgstLedger = "Output SGST @ " + halfRate.stripTrailingZeros().toPlainString() + "%";
            appendLedgerEntry(doc, voucher, sgstLedger, "No", sgst.toPlainString());
        }
    }

    private void appendLedgerEntry(Document doc, Element parent,
                                   String ledgerName, String isDeemedPositive, String amount) {
        Element entry = child(doc, parent, "LEDGERENTRIES.LIST");
        child(doc, entry, "LEDGERNAME").setTextContent(ledgerName);
        child(doc, entry, "ISDEEMEDPOSITIVE").setTextContent(isDeemedPositive);
        child(doc, entry, "AMOUNT").setTextContent(amount);
    }

    // ── XML helpers ───────────────────────────────────────────────────────────

    private Element child(Document doc, Element parent, String tag) {
        Element el = doc.createElement(tag);
        parent.appendChild(el);
        return el;
    }

    // ── Excel style helpers ───────────────────────────────────────────────────

    private CellStyle makeStyle(Workbook wb, boolean bold, int fontSize, IndexedColors bgColor) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        if (bgColor != null) {
            font.setColor(bgColor.getIndex());
        }
        style.setFont(font);
        return style;
    }

    private CellStyle makeAmountStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private void setCell(Sheet sheet, int rowIdx, int colIdx, String value, CellStyle style) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void addAmountRow(Sheet sheet, int rowIdx, String label,
                              BigDecimal amount, CellStyle labelStyle, CellStyle amtStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell lbl = row.createCell(0);
        lbl.setCellValue(label);
        lbl.setCellStyle(labelStyle);
        Cell val = row.createCell(1);
        val.setCellValue(amount.doubleValue());
        val.setCellStyle(amtStyle);
    }

    private void setTextCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setNumCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
