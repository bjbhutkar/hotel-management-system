package com.hotel.service;

import com.hotel.entity.Order;
import com.hotel.repository.OrderItemRepository;
import com.hotel.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public Map<String, Object> getDailyReport(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.atTime(LocalTime.MAX);
        return buildReport("Daily Sales Report — " + date, start, end);
    }

    public Map<String, Object> getWeeklyReport(LocalDate weekStart) {
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end   = weekStart.plusDays(6).atTime(LocalTime.MAX);
        String title = "Weekly Sales Report — " + weekStart + " to " + weekStart.plusDays(6);
        return buildReport(title, start, end);
    }

    public Map<String, Object> getMonthlyReport(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDateTime start = first.atStartOfDay();
        LocalDateTime end   = first.withDayOfMonth(first.lengthOfMonth()).atTime(LocalTime.MAX);
        String title = "Monthly Sales Report — " + Month.of(month).name() + " " + year;
        return buildReport(title, start, end);
    }

    public List<Order> getCancelledOrders(LocalDateTime start, LocalDateTime end) {
        return orderRepository.findByStatusAndCreatedAtBetween(Order.OrderStatus.CANCELLED, start, end);
    }

    public List<Object[]> getMostSoldItems(LocalDateTime start, LocalDateTime end) {
        return orderItemRepository.findMostSoldItems(start, end).stream().limit(10).toList();
    }

    public List<Object[]> getLeastSoldItems(LocalDateTime start, LocalDateTime end) {
        return orderItemRepository.findLeastSoldItems(start, end).stream().limit(10).toList();
    }

    public Map<LocalDate, BigDecimal> getDailyRevenue(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end   = to.atTime(LocalTime.MAX);
        List<Order> orders  = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        Map<LocalDate, BigDecimal> daily = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            daily.put(d, BigDecimal.ZERO);
        }
        orders.stream()
              .filter(o -> o.getStatus() == Order.OrderStatus.BILLED)
              .forEach(o -> daily.merge(o.getCreatedAt().toLocalDate(), o.getTotalAmount(), BigDecimal::add));
        return daily;
    }

    // -----------------------------------------------------------------------

    private Map<String, Object> buildReport(String title, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("title", title);
        report.put("period_start", start);
        report.put("period_end", end);

        List<Order> allOrders    = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        List<Order> billed       = allOrders.stream()
                                       .filter(o -> o.getStatus() == Order.OrderStatus.BILLED).toList();
        List<Order> cancelled    = allOrders.stream()
                                       .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED).toList();

        BigDecimal revenue  = billed.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = billed.stream().map(Order::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax      = billed.stream().map(Order::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        report.put("total_orders",    allOrders.size());
        report.put("billed_orders",   billed.size());
        report.put("cancelled_orders",cancelled.size());
        report.put("subtotal",        subtotal);
        report.put("tax_collected",   tax);
        report.put("total_revenue",   revenue);

        report.put("most_sold_items",  orderItemRepository.findMostSoldItems(start, end).stream().limit(5).toList());
        report.put("least_sold_items", orderItemRepository.findLeastSoldItems(start, end).stream().limit(5).toList());
        report.put("billed_orders_list", billed);

        return report;
    }
}
