package com.hotel.service;

import com.hotel.entity.Order;
import com.hotel.entity.Order.OrderStatus;
import com.hotel.repository.OrderItemRepository;
import com.hotel.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @InjectMocks private ReportService reportService;

    private Order billedOrder;
    private Order cancelledOrder;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        billedOrder = Order.builder()
                .id(1L).orderNumber("ORD-A").tableNumber(1)
                .status(OrderStatus.BILLED)
                .subtotal(new BigDecimal("500.00"))
                .taxRate(new BigDecimal("18"))
                .taxAmount(new BigDecimal("90.00"))
                .totalAmount(new BigDecimal("590.00"))
                .createdAt(LocalDateTime.of(2026, 5, 15, 12, 0))
                .items(new ArrayList<>()).build();

        cancelledOrder = Order.builder()
                .id(2L).orderNumber("ORD-B").tableNumber(2)
                .status(OrderStatus.CANCELLED)
                .subtotal(BigDecimal.ZERO).taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO).totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 5, 15, 14, 0))
                .items(new ArrayList<>()).build();

        pendingOrder = Order.builder()
                .id(3L).orderNumber("ORD-C").tableNumber(3)
                .status(OrderStatus.PENDING)
                .subtotal(BigDecimal.ZERO).taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO).totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.of(2026, 5, 15, 15, 0))
                .items(new ArrayList<>()).build();
    }

    // -----------------------------------------------------------------------
    // getDailyReport
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDailyReport")
    class DailyReport {

        @Test @DisplayName("queries from start-of-day to end-of-day")
        void queriesCorrectDateRange() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCaptor   = ArgumentCaptor.forClass(LocalDateTime.class);

            reportService.getDailyReport(date);
            verify(orderRepository).findByCreatedAtBetweenOrderByCreatedAtDesc(
                    startCaptor.capture(), endCaptor.capture());

            assertThat(startCaptor.getValue()).isEqualTo(date.atStartOfDay());
            assertThat(endCaptor.getValue().toLocalDate()).isEqualTo(date);
            assertThat(endCaptor.getValue().toLocalTime()).isEqualTo(LocalTime.MAX);
        }

        @Test @DisplayName("report title contains the queried date")
        void titleContainsDate() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getDailyReport(date);
            assertThat(report.get("title").toString()).contains("2026-05-15");
        }

        @Test @DisplayName("counts billed, cancelled, and total orders correctly")
        void countsOrdersByStatus() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(billedOrder, cancelledOrder, pendingOrder));
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getDailyReport(date);
            assertThat(report.get("total_orders")).isEqualTo(3);
            assertThat(report.get("billed_orders")).isEqualTo(1);
            assertThat(report.get("cancelled_orders")).isEqualTo(1);
        }

        @Test @DisplayName("revenue is sum of billed orders only")
        void revenueFromBilledOrdersOnly() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(billedOrder, cancelledOrder));
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getDailyReport(date);
            assertThat((BigDecimal) report.get("total_revenue"))
                    .isEqualByComparingTo("590.00");
        }

        @Test @DisplayName("tax_collected is sum of billed orders' taxAmount")
        void taxCollectedFromBilledOrders() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(billedOrder));
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getDailyReport(date);
            assertThat((BigDecimal) report.get("tax_collected"))
                    .isEqualByComparingTo("90.00");
        }

        @Test @DisplayName("revenue is zero when no billed orders")
        void revenueZeroWhenNoBilledOrders() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(cancelledOrder, pendingOrder));
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getDailyReport(date);
            assertThat((BigDecimal) report.get("total_revenue"))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // -----------------------------------------------------------------------
    // getWeeklyReport
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getWeeklyReport")
    class WeeklyReport {

        @Test @DisplayName("end date is exactly 6 days after start date")
        void endDateIs6DaysAfterStart() {
            LocalDate weekStart = LocalDate.of(2026, 5, 11);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            reportService.getWeeklyReport(weekStart);

            verify(orderRepository).findByCreatedAtBetweenOrderByCreatedAtDesc(
                    any(), endCaptor.capture());
            assertThat(endCaptor.getValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 5, 17));
        }

        @Test @DisplayName("report title contains week start and end dates")
        void titleContainsWeekRange() {
            LocalDate weekStart = LocalDate.of(2026, 5, 11);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getWeeklyReport(weekStart);
            String title = report.get("title").toString();
            assertThat(title).contains("2026-05-11");
            assertThat(title).contains("2026-05-17");
        }
    }

    // -----------------------------------------------------------------------
    // getMonthlyReport
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getMonthlyReport")
    class MonthlyReport {

        @Test @DisplayName("queries from first to last day of the month")
        void queriesFullMonth() {
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCaptor   = ArgumentCaptor.forClass(LocalDateTime.class);

            reportService.getMonthlyReport(2026, 5);
            verify(orderRepository).findByCreatedAtBetweenOrderByCreatedAtDesc(
                    startCaptor.capture(), endCaptor.capture());

            assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 5, 1).atStartOfDay());
            assertThat(endCaptor.getValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        }

        @Test @DisplayName("report title contains month name and year")
        void titleContainsMonthAndYear() {
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            Map<String, Object> report = reportService.getMonthlyReport(2026, 5);
            String title = report.get("title").toString();
            assertThat(title).containsIgnoringCase(Month.MAY.name());
            assertThat(title).contains("2026");
        }

        @Test @DisplayName("handles February correctly (28 days in 2026)")
        void handlesFebNonLeapYear() {
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(orderItemRepository.findMostSoldItems(any(), any())).thenReturn(List.of());
            when(orderItemRepository.findLeastSoldItems(any(), any())).thenReturn(List.of());

            ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            reportService.getMonthlyReport(2026, 2);
            verify(orderRepository).findByCreatedAtBetweenOrderByCreatedAtDesc(
                    any(), endCaptor.capture());

            assertThat(endCaptor.getValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 2, 28));
        }
    }

    // -----------------------------------------------------------------------
    // getDailyRevenue
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDailyRevenue")
    class DailyRevenue {

        @Test @DisplayName("returns an entry for every day in the range")
        void returnsEntryForEveryDay() {
            LocalDate from = LocalDate.of(2026, 5, 13);
            LocalDate to   = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());

            Map<LocalDate, BigDecimal> revenue = reportService.getDailyRevenue(from, to);
            assertThat(revenue).containsKeys(
                    LocalDate.of(2026, 5, 13),
                    LocalDate.of(2026, 5, 14),
                    LocalDate.of(2026, 5, 15));
        }

        @Test @DisplayName("sums billed-order totals into the correct day bucket")
        void sumsBilledRevenuePerDay() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(billedOrder));

            Map<LocalDate, BigDecimal> revenue = reportService.getDailyRevenue(date, date);
            assertThat(revenue.get(date)).isEqualByComparingTo("590.00");
        }

        @Test @DisplayName("excludes non-billed orders from revenue")
        void excludesNonBilledOrders() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of(cancelledOrder, pendingOrder));

            Map<LocalDate, BigDecimal> revenue = reportService.getDailyRevenue(date, date);
            assertThat(revenue.get(date)).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // -----------------------------------------------------------------------
    // getCancelledOrders
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getCancelledOrders")
    class CancelledOrders {

        @Test @DisplayName("delegates to repository with CANCELLED status")
        void delegatesToRepository() {
            LocalDateTime start = LocalDateTime.of(2026, 5, 15, 0, 0);
            LocalDateTime end   = LocalDateTime.of(2026, 5, 15, 23, 59);
            when(orderRepository.findByStatusAndCreatedAtBetween(
                    OrderStatus.CANCELLED, start, end))
                    .thenReturn(List.of(cancelledOrder));

            List<Order> result = reportService.getCancelledOrders(start, end);
            assertThat(result).containsExactly(cancelledOrder);
        }
    }
}
