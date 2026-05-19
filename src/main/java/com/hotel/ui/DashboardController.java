package com.hotel.ui;

import com.hotel.delivery.service.OnlineOrderProcessingService;
import com.hotel.entity.Order;
import com.hotel.service.OrderService;
import com.hotel.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label todayOrdersLabel;
    @FXML private Label todayRevenueLabel;
    @FXML private Label activeOrdersLabel;
    @FXML private Label cancelledTodayLabel;
    @FXML private Label onlineOrdersLabel;

    private final UserService                 userService;
    private final OrderService                orderService;
    private final OnlineOrderProcessingService onlineOrderProcessingService;
    private final StageManager                stageManager;

    @FXML
    public void initialize() {
        welcomeLabel.setText("Welcome, " + userService.getCurrentUser().getFullName() + "!");
        refreshStats();
    }

    @FXML
    public void refreshDashboard() {
        refreshStats();
    }

    private void refreshStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = LocalDate.now().atTime(LocalTime.MAX);

        List<Order> todayOrders  = orderService.getOrdersByDateRange(startOfDay, endOfDay);
        List<Order> activeOrders = orderService.getActiveOrders();

        long cancelledCount = todayOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED).count();

        BigDecimal revenue = todayOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.BILLED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        todayOrdersLabel.setText(String.valueOf(todayOrders.size()));
        todayRevenueLabel.setText("₹" + revenue.setScale(2, java.math.RoundingMode.HALF_UP));
        activeOrdersLabel.setText(String.valueOf(activeOrders.size()));
        cancelledTodayLabel.setText(String.valueOf(cancelledCount));
        if (onlineOrdersLabel != null)
            onlineOrdersLabel.setText(String.valueOf(onlineOrderProcessingService.countNewOrders()));
    }

    @FXML public void openOrderManagement()  throws IOException { stageManager.showOrderManagement(); }
    @FXML public void openMenuManagement()   throws IOException { stageManager.showMenuManagement(); }
    @FXML public void openReports()          throws IOException { stageManager.showReports(); }
    @FXML public void openOnlineOrders()     throws IOException { stageManager.showOnlineOrdersDashboard(); }
    @FXML public void openSettings()         throws IOException { stageManager.showSettings(); }
    @FXML public void openImportData()       throws IOException { stageManager.showImportData(); }
    @FXML public void handleLogout()         throws IOException {
        userService.logout();
        stageManager.showLoginScreen();
    }
}
