package com.hotel.service;

import com.hotel.entity.Order;
import com.hotel.entity.RestaurantConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final OrderService            orderService;
    private final RestaurantConfigService configService;

    /**
     * Generates a formatted text invoice for the given order.
     * All header/footer fields are sourced from RestaurantConfig.
     */
    public String generateInvoiceText(Order order) {
        RestaurantConfig cfg = configService.getConfig();
        StringBuilder sb  = new StringBuilder();
        String eq  = "=".repeat(48);
        String sep = "-".repeat(48);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        String currency = cfg.getCurrencySymbol();

        sb.append(eq).append("\n");
        sb.append(center(cfg.getRestaurantName(), 48)).append("\n");
        if (cfg.getAddress() != null && !cfg.getAddress().isBlank()) {
            for (String line : wordWrap(cfg.getAddress(), 48)) {
                sb.append(center(line, 48)).append("\n");
            }
        }
        if (cfg.getPhoneNumber() != null && !cfg.getPhoneNumber().isBlank())
            sb.append(center("Ph: " + cfg.getPhoneNumber(), 48)).append("\n");
        if (cfg.getGstNumber() != null && !cfg.getGstNumber().isBlank())
            sb.append(center("GSTIN: " + cfg.getGstNumber(), 48)).append("\n");
        if (cfg.getFssaiNumber() != null && !cfg.getFssaiNumber().isBlank())
            sb.append(center("FSSAI: " + cfg.getFssaiNumber(), 48)).append("\n");
        sb.append(center("INVOICE", 48)).append("\n");
        sb.append(eq).append("\n");

        sb.append(String.format("Invoice No  : %s-%s%n", cfg.getInvoicePrefix(), order.getOrderNumber()));
        sb.append(String.format("Date & Time : %s%n",  order.getCreatedAt().format(dtf)));
        sb.append(String.format("Table No    : %d%n",  order.getTableNumber()));
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank())
            sb.append(String.format("Customer    : %s%n", order.getCustomerName()));
        if (order.getCreatedBy() != null)
            sb.append(String.format("Served by   : %s%n", order.getCreatedBy().getFullName()));

        sb.append(sep).append("\n");
        sb.append(String.format("%-26s %5s %10s%n", "Item", "Qty", "Amount"));
        sb.append(sep).append("\n");

        order.getItems().forEach(item -> {
            sb.append(String.format("%-26s %5d %10.2f%n",
                    truncate(item.getMenuItem().getName(), 26),
                    item.getQuantity(),
                    item.getTotalPrice()));
            sb.append(String.format("  @ %s%.2f each%n", currency, item.getUnitPrice()));
        });

        sb.append(sep).append("\n");
        sb.append(String.format("%-33s %10.2f%n", "Subtotal:",    order.getSubtotal()));
        if (order.getTaxRate().signum() > 0) {
            sb.append(String.format("%-33s %10.2f%n",
                    String.format("GST @ %.0f%%:", order.getTaxRate()), order.getTaxAmount()));
        }
        sb.append(eq).append("\n");
        sb.append(String.format("%-33s %s%10.2f%n", "TOTAL AMOUNT:", currency, order.getTotalAmount()));
        sb.append(eq).append("\n");

        String thanks = cfg.getThankYouMessage();
        if (thanks != null && !thanks.isBlank())
            sb.append(center(thanks, 48)).append("\n");
        String footer = cfg.getFooterMessage();
        if (footer != null && !footer.isBlank())
            sb.append(center(footer, 48)).append("\n");
        sb.append(eq).append("\n");

        return sb.toString();
    }

    /** Marks the order as billed and logs the print action. */
    public void printInvoice(Order order) {
        orderService.markAsBilled(order.getId());
        String invoice = generateInvoiceText(orderService.getOrderById(order.getId()).orElse(order));
        log.info("Invoice printed for order {}", order.getOrderNumber());
        System.out.println(invoice);
    }

    /** Saves invoice as a .txt file to the specified path. */
    public void saveInvoiceToFile(Order order, String filePath) throws IOException {
        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(generateInvoiceText(order));
        }
        log.info("Invoice for {} saved to {}", order.getOrderNumber(), filePath);
    }

    /** Generates a Kitchen Order Ticket text for kitchen printing. */
    public String generateKotText(Order order) {
        StringBuilder sb  = new StringBuilder();
        String sep = "-".repeat(36);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        sb.append("*** KITCHEN ORDER TICKET ***\n");
        sb.append(sep).append("\n");
        sb.append(String.format("Order  : %s%n", order.getOrderNumber()));
        sb.append(String.format("Table  : %d%n", order.getTableNumber()));
        sb.append(String.format("Time   : %s%n", order.getCreatedAt().format(dtf)));
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank())
            sb.append(String.format("Guest  : %s%n", order.getCustomerName()));
        sb.append(sep).append("\n");
        sb.append(String.format("%-22s  %s%n", "ITEM", "QTY"));
        sb.append(sep).append("\n");

        order.getItems().forEach(item -> {
            sb.append(String.format("%-22s  %d%n",
                    truncate(item.getMenuItem().getName(), 22),
                    item.getQuantity()));
            if (item.getNotes() != null && !item.getNotes().isBlank())
                sb.append(String.format("  >> %s%n", item.getNotes()));
        });

        sb.append(sep).append("\n");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 2) + ".." : str;
    }

    private String center(String text, int width) {
        if (text == null || text.length() >= width) return text;
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text;
    }

    private String[] wordWrap(String text, int width) {
        if (text.length() <= width) return new String[]{text};
        return text.split("(?<=\\G.{" + width + "})");
    }
}
