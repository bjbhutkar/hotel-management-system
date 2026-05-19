package com.hotel.service;

import com.hotel.entity.MenuItem;
import com.hotel.entity.Order;
import com.hotel.entity.Order.OrderStatus;
import com.hotel.entity.OrderItem;
import com.hotel.entity.User;
import com.hotel.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserService userService;
    @InjectMocks private OrderService orderService;

    private User staff;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        staff = User.builder()
                .id(1L).username("staff").fullName("Staff Member")
                .role(User.Role.STAFF).active(true).build();

        menuItem = MenuItem.builder()
                .id(1L).name("Butter Chicken")
                .price(new BigDecimal("280.00"))
                .available(true).build();

        // Needed only by createOrder tests; lenient avoids UnnecessaryStubbingException
        // in the many tests that never call generateOrderNumber().
        lenient().when(orderRepository.findMaxCounterForDate(anyString())).thenReturn(0);
    }

    private Order savedOrder(String orderNumber, int table) {
        return Order.builder()
                .id(1L)
                .orderNumber(orderNumber)
                .tableNumber(table)
                .status(OrderStatus.PENDING)
                .subtotal(BigDecimal.ZERO)
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();
    }

    // -----------------------------------------------------------------------
    // createOrder
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test @DisplayName("returns saved order with PENDING status")
        void returnsPendingOrder() {
            when(userService.getCurrentUser()).thenReturn(staff);
            Order stub = savedOrder("ORD-20260515-0001", 3);
            when(orderRepository.save(any())).thenReturn(stub);

            Order result = orderService.createOrder(3, "Ravi");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test @DisplayName("order number matches ORD-YYYYMMDD-NNNN format")
        void orderNumberMatchesFormat() {
            when(userService.getCurrentUser()).thenReturn(staff);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.createOrder(1, null);
            assertThat(result.getOrderNumber()).matches("ORD-\\d{8}-\\d{4}");
        }

        @Test @DisplayName("stores table number on order")
        void storesTableNumber() {
            when(userService.getCurrentUser()).thenReturn(staff);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.createOrder(7, "Test");
            assertThat(result.getTableNumber()).isEqualTo(7);
        }

        @Test @DisplayName("stores customer name on order")
        void storesCustomerName() {
            when(userService.getCurrentUser()).thenReturn(staff);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.createOrder(2, "Priya");
            assertThat(result.getCustomerName()).isEqualTo("Priya");
        }

        @Test @DisplayName("persists created order via repository")
        void persistsOrder() {
            when(userService.getCurrentUser()).thenReturn(staff);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            orderService.createOrder(1, "Test");
            verify(orderRepository).save(any(Order.class));
        }

        @Test @DisplayName("sequential orders in same day have incrementing numbers")
        void sequentialOrderNumbers() {
            when(userService.getCurrentUser()).thenReturn(staff);
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order first  = orderService.createOrder(1, null);
            Order second = orderService.createOrder(2, null);
            assertThat(first.getOrderNumber()).isNotEqualTo(second.getOrderNumber());
        }
    }

    // -----------------------------------------------------------------------
    // addItemToOrder + recalculateTotals
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addItemToOrder (covers recalculateTotals)")
    class AddItemToOrder {

        private Order pendingOrder;

        @BeforeEach
        void buildPendingOrder() {
            pendingOrder = Order.builder()
                    .id(1L).orderNumber("ORD-20260515-0001").tableNumber(3)
                    .status(OrderStatus.PENDING)
                    .subtotal(BigDecimal.ZERO).taxRate(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO).totalAmount(BigDecimal.ZERO)
                    .createdAt(LocalDateTime.now())
                    .items(new ArrayList<>())
                    .build();
        }

        @Test @DisplayName("adds item and recalculates subtotal correctly")
        void recalculatesSubtotal() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.addItemToOrder(1L, menuItem, 2, null);
            // 2 × 280.00 = 560.00
            assertThat(result.getSubtotal()).isEqualByComparingTo("560.00");
        }

        @Test @DisplayName("totalAmount equals subtotal when taxRate is zero")
        void totalEqualsSubtotal_whenNoTax() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.addItemToOrder(1L, menuItem, 1, null);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(result.getSubtotal());
        }

        @Test @DisplayName("GST calculation: 18% on 560.00 → taxAmount 100.80")
        void gstCalculation() {
            pendingOrder.setTaxRate(new BigDecimal("18"));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.addItemToOrder(1L, menuItem, 2, null);
            assertThat(result.getTaxAmount()).isEqualByComparingTo("100.80");
            assertThat(result.getTotalAmount()).isEqualByComparingTo("660.80");
        }

        @Test @DisplayName("status transitions from PENDING to IN_PROGRESS after first item")
        void statusBecomesInProgress() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.addItemToOrder(1L, menuItem, 1, null);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        }

        @Test @DisplayName("adding the same item again increments quantity")
        void addSameItem_incrementsQuantity() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            orderService.addItemToOrder(1L, menuItem, 2, null);
            Order result = orderService.addItemToOrder(1L, menuItem, 1, null);

            OrderItem line = result.getItems().get(0);
            assertThat(line.getQuantity()).isEqualTo(3);
        }

        @Test @DisplayName("throws when order not found")
        void throwsWhenOrderNotFound() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> orderService.addItemToOrder(99L, menuItem, 1, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // -----------------------------------------------------------------------
    // removeItemFromOrder
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("removeItemFromOrder")
    class RemoveItem {

        @Test @DisplayName("removes item and recalculates subtotal to zero")
        void removesItemAndRecalculates() {
            MenuItem item = MenuItem.builder().id(1L).name("Naan")
                    .price(new BigDecimal("40.00")).build();
            OrderItem line = OrderItem.builder().id(10L).menuItem(item)
                    .quantity(1).unitPrice(new BigDecimal("40.00"))
                    .totalPrice(new BigDecimal("40.00")).build();
            Order o = Order.builder().id(1L).orderNumber("X").tableNumber(1)
                    .status(OrderStatus.IN_PROGRESS)
                    .subtotal(new BigDecimal("40.00")).taxRate(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("40.00"))
                    .createdAt(LocalDateTime.now())
                    .items(new ArrayList<>(List.of(line))).build();
            line.setOrder(o);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.removeItemFromOrder(1L, 10L);
            assertThat(result.getItems()).isEmpty();
            assertThat(result.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // -----------------------------------------------------------------------
    // cancelOrder
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test @DisplayName("sets status to CANCELLED")
        void setsStatusCancelled() {
            Order o = savedOrder("ORD-X", 2);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.cancelOrder(1L, "Customer left");
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test @DisplayName("stores cancellation reason")
        void storesCancellationReason() {
            Order o = savedOrder("ORD-X", 2);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.cancelOrder(1L, "Customer left");
            assertThat(result.getCancellationReason()).isEqualTo("Customer left");
        }

        @Test @DisplayName("throws when order not found")
        void throwsWhenNotFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> orderService.cancelOrder(999L, "reason"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // markAsBilled
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("markAsBilled")
    class MarkAsBilled {

        @Test @DisplayName("sets status to BILLED")
        void setsStatusBilled() {
            Order o = savedOrder("ORD-X", 1);
            o.setStatus(OrderStatus.COMPLETED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.markAsBilled(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.BILLED);
        }

        @Test @DisplayName("sets completedAt when it was null")
        void setsCompletedAt_whenNull() {
            Order o = savedOrder("ORD-X", 1);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.markAsBilled(1L);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test @DisplayName("does not overwrite existing completedAt")
        void doesNotOverwriteCompletedAt() {
            LocalDateTime original = LocalDateTime.of(2026, 5, 15, 12, 0);
            Order o = savedOrder("ORD-X", 1);
            o.setCompletedAt(original);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.markAsBilled(1L);
            assertThat(result.getCompletedAt()).isEqualTo(original);
        }
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getActiveOrders")
    class GetActiveOrders {

        @Test @DisplayName("queries PENDING, IN_PROGRESS, COMPLETED, BILLED statuses")
        void queriesCorrectStatuses() {
            when(orderRepository.findByStatusIn(anyList())).thenReturn(List.of());
            orderService.getActiveOrders();
            verify(orderRepository).findByStatusIn(argThat(list ->
                    list.contains(OrderStatus.PENDING)
                    && list.contains(OrderStatus.IN_PROGRESS)
                    && list.contains(OrderStatus.COMPLETED)
                    && list.contains(OrderStatus.BILLED)));
        }

        @Test @DisplayName("does not include CANCELLED in active query")
        void excludesCancelled() {
            when(orderRepository.findByStatusIn(anyList())).thenReturn(List.of());
            orderService.getActiveOrders();
            verify(orderRepository).findByStatusIn(argThat(list ->
                    !list.contains(OrderStatus.CANCELLED)));
        }
    }
}
