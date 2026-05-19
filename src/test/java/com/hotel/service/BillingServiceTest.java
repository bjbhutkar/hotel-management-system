package com.hotel.service;

import com.hotel.entity.MenuItem;
import com.hotel.entity.Order;
import com.hotel.entity.OrderItem;
import com.hotel.entity.RestaurantConfig;
import com.hotel.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingService")
class BillingServiceTest {

    @Mock  private OrderService            orderService;
    @Mock  private RestaurantConfigService configService;
    @InjectMocks private BillingService billingService;

    private Order order;

    @BeforeEach
    void setUp() {
        RestaurantConfig cfg = RestaurantConfig.builder()
                .id(1L)
                .restaurantName("Rasoi Restaurant")
                .currencySymbol("₹")
                .invoicePrefix("INV")
                .defaultTaxPercent(new BigDecimal("18"))
                .thankYouMessage("Thank you for dining with us!")
                .build();
        lenient().when(configService.getConfig()).thenReturn(cfg);
        MenuItem menuItem = MenuItem.builder()
                .id(1L).name("Butter Chicken").price(new BigDecimal("280.00")).build();

        OrderItem line = OrderItem.builder()
                .id(1L).menuItem(menuItem).quantity(2)
                .unitPrice(new BigDecimal("280.00"))
                .totalPrice(new BigDecimal("560.00")).build();

        User staff = User.builder()
                .id(1L).username("staff").fullName("Staff Member").build();

        order = Order.builder()
                .id(1L)
                .orderNumber("ORD-20260515-0001")
                .tableNumber(5)
                .customerName("Amit Shah")
                .createdBy(staff)
                .createdAt(LocalDateTime.of(2026, 5, 15, 13, 30))
                .subtotal(new BigDecimal("560.00"))
                .taxRate(new BigDecimal("18"))
                .taxAmount(new BigDecimal("100.80"))
                .totalAmount(new BigDecimal("660.80"))
                .items(new ArrayList<>(List.of(line)))
                .build();

        line.setOrder(order);
    }

    // -----------------------------------------------------------------------
    // Invoice content
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("generateInvoiceText content")
    class InvoiceContent {

        @Test @DisplayName("contains RASOI brand name")
        void containsBrand() {
            assertThat(billingService.generateInvoiceText(order))
                    .containsIgnoringCase("RASOI");
        }

        @Test @DisplayName("contains INVOICE keyword")
        void containsInvoiceKeyword() {
            assertThat(billingService.generateInvoiceText(order))
                    .containsIgnoringCase("INVOICE");
        }

        @Test @DisplayName("contains order number")
        void containsOrderNumber() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("ORD-20260515-0001");
        }

        @Test @DisplayName("contains table number")
        void containsTableNumber() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("5");
        }

        @Test @DisplayName("contains customer name")
        void containsCustomerName() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("Amit Shah");
        }

        @Test @DisplayName("contains served-by staff name")
        void containsServedBy() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("Staff Member");
        }

        @Test @DisplayName("contains ordered item name")
        void containsItemName() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("Butter Chicken");
        }

        @Test @DisplayName("contains correct subtotal")
        void containsSubtotal() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("560.00");
        }

        @Test @DisplayName("contains GST amount")
        void containsGst() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("100.80");
        }

        @Test @DisplayName("contains correct grand total")
        void containsTotal() {
            assertThat(billingService.generateInvoiceText(order))
                    .contains("660.80");
        }

        @Test @DisplayName("omits Customer line when name is blank")
        void omitsCustomerLine_whenNameBlank() {
            order.setCustomerName("");
            assertThat(billingService.generateInvoiceText(order))
                    .doesNotContain("Customer");
        }

        @Test @DisplayName("omits Customer line when name is null")
        void omitsCustomerLine_whenNameNull() {
            order.setCustomerName(null);
            assertThat(billingService.generateInvoiceText(order))
                    .doesNotContain("Customer");
        }

        @Test @DisplayName("contains thank-you closing message")
        void containsThankYou() {
            assertThat(billingService.generateInvoiceText(order))
                    .containsIgnoringCase("thank you");
        }
    }

    // -----------------------------------------------------------------------
    // Save to file
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("saveInvoiceToFile")
    class SaveToFile {

        @Test @DisplayName("creates the output file")
        void createsFile(@TempDir Path tmp) throws IOException {
            Path out = tmp.resolve("invoice.txt");
            billingService.saveInvoiceToFile(order, out.toString());
            assertThat(out).exists();
        }

        @Test @DisplayName("written file contains order number")
        void fileContainsOrderNumber(@TempDir Path tmp) throws IOException {
            Path out = tmp.resolve("invoice.txt");
            billingService.saveInvoiceToFile(order, out.toString());
            assertThat(Files.readString(out)).contains("ORD-20260515-0001");
        }

        @Test @DisplayName("written file contains RASOI brand name")
        void fileContainsBrand(@TempDir Path tmp) throws IOException {
            Path out = tmp.resolve("invoice.txt");
            billingService.saveInvoiceToFile(order, out.toString());
            assertThat(Files.readString(out)).containsIgnoringCase("RASOI");
        }
    }

    // -----------------------------------------------------------------------
    // printInvoice
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("printInvoice")
    class PrintInvoice {

        @Test @DisplayName("delegates markAsBilled to OrderService")
        void callsMarkAsBilled() {
            when(orderService.markAsBilled(1L)).thenReturn(order);
            when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));
            billingService.printInvoice(order);
            verify(orderService).markAsBilled(1L);
        }

        @Test @DisplayName("fetches fresh order state after marking billed")
        void fetchesFreshOrder() {
            when(orderService.markAsBilled(1L)).thenReturn(order);
            when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));
            billingService.printInvoice(order);
            verify(orderService).getOrderById(1L);
        }
    }
}
