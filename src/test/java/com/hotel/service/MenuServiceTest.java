package com.hotel.service;

import com.hotel.entity.MenuItem;
import com.hotel.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuService")
class MenuServiceTest {

    @Mock private MenuItemRepository menuItemRepository;
    @InjectMocks private MenuService menuService;

    private MenuItem available;
    private MenuItem unavailable;

    @BeforeEach
    void setUp() {
        available = MenuItem.builder()
                .id(1L).name("Butter Chicken").category("Main Course")
                .price(new BigDecimal("280.00")).available(true).build();

        unavailable = MenuItem.builder()
                .id(2L).name("Fish Curry").category("Main Course")
                .price(new BigDecimal("320.00")).available(false).build();
    }

    // -----------------------------------------------------------------------
    // addMenuItem
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addMenuItem")
    class AddMenuItem {

        @Test @DisplayName("saves item with correct name")
        void savesName() {
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            MenuItem result = menuService.addMenuItem("Dal Tadka", "Lentils", "Main Course",
                    new BigDecimal("160.00"));
            assertThat(result.getName()).isEqualTo("Dal Tadka");
        }

        @Test @DisplayName("saves item with correct price")
        void savesPrice() {
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            MenuItem result = menuService.addMenuItem("Naan", null, "Breads",
                    new BigDecimal("40.00"));
            assertThat(result.getPrice()).isEqualByComparingTo("40.00");
        }

        @Test @DisplayName("saves item with correct category")
        void savesCategory() {
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            MenuItem result = menuService.addMenuItem("Masala Chai", null, "Beverages",
                    new BigDecimal("30.00"));
            assertThat(result.getCategory()).isEqualTo("Beverages");
        }

        @Test @DisplayName("new item is available by default")
        void newItemIsAvailable() {
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            MenuItem result = menuService.addMenuItem("Ice Cream", null, "Desserts",
                    new BigDecimal("70.00"));
            assertThat(result.isAvailable()).isTrue();
        }

        @Test @DisplayName("delegates persistence to repository")
        void delegatesToRepository() {
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            menuService.addMenuItem("Roti", null, "Breads", new BigDecimal("25.00"));
            verify(menuItemRepository).save(any(MenuItem.class));
        }
    }

    // -----------------------------------------------------------------------
    // updateMenuItem
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateMenuItem")
    class UpdateMenuItem {

        @Test @DisplayName("updates name")
        void updatesName() {
            when(menuItemRepository.findById(1L)).thenReturn(Optional.of(available));
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MenuItem result = menuService.updateMenuItem(1L, "Murgh Makhani",
                    "Updated description", "Main Course", new BigDecimal("300.00"), true);
            assertThat(result.getName()).isEqualTo("Murgh Makhani");
        }

        @Test @DisplayName("updates price")
        void updatesPrice() {
            when(menuItemRepository.findById(1L)).thenReturn(Optional.of(available));
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MenuItem result = menuService.updateMenuItem(1L, "Butter Chicken",
                    null, "Main Course", new BigDecimal("320.00"), true);
            assertThat(result.getPrice()).isEqualByComparingTo("320.00");
        }

        @Test @DisplayName("can mark item as unavailable")
        void marksUnavailable() {
            when(menuItemRepository.findById(1L)).thenReturn(Optional.of(available));
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MenuItem result = menuService.updateMenuItem(1L, "Butter Chicken",
                    null, "Main Course", new BigDecimal("280.00"), false);
            assertThat(result.isAvailable()).isFalse();
        }

        @Test @DisplayName("throws when item not found")
        void throwsWhenNotFound() {
            when(menuItemRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> menuService.updateMenuItem(999L, "X", null, "Y",
                    BigDecimal.TEN, true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Menu item not found");
        }
    }

    // -----------------------------------------------------------------------
    // toggleAvailability
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toggleAvailability")
    class ToggleAvailability {

        @Test @DisplayName("flips available → unavailable")
        void flipsToUnavailable() {
            when(menuItemRepository.findById(1L)).thenReturn(Optional.of(available));
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            menuService.toggleAvailability(1L);
            assertThat(available.isAvailable()).isFalse();
        }

        @Test @DisplayName("flips unavailable → available")
        void flipsToAvailable() {
            when(menuItemRepository.findById(2L)).thenReturn(Optional.of(unavailable));
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            menuService.toggleAvailability(2L);
            assertThat(unavailable.isAvailable()).isTrue();
        }

        @Test @DisplayName("throws when item not found")
        void throwsWhenNotFound() {
            when(menuItemRepository.findById(404L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> menuService.toggleAvailability(404L))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // deleteMenuItem
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteMenuItem")
    class DeleteMenuItem {

        @Test @DisplayName("calls deleteById on repository")
        void callsDeleteById() {
            menuService.deleteMenuItem(1L);
            verify(menuItemRepository).deleteById(1L);
        }
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getAvailableMenuItems")
    class GetAvailable {

        @Test @DisplayName("delegates to findByAvailableTrue")
        void delegatesToFindByAvailableTrue() {
            when(menuItemRepository.findByAvailableTrue()).thenReturn(List.of(available));
            List<MenuItem> result = menuService.getAvailableMenuItems();
            assertThat(result).containsExactly(available);
            verify(menuItemRepository).findByAvailableTrue();
        }
    }

    @Nested
    @DisplayName("getMenuItemsByCategory")
    class GetByCategory {

        @Test @DisplayName("delegates to findByCategoryAndAvailableTrue")
        void delegatesToFindByCategoryAndAvailableTrue() {
            when(menuItemRepository.findByCategoryAndAvailableTrue("Main Course"))
                    .thenReturn(List.of(available));
            List<MenuItem> result = menuService.getMenuItemsByCategory("Main Course");
            assertThat(result).containsExactly(available);
        }
    }

    @Nested
    @DisplayName("initializeDefaultMenu")
    class InitDefaultMenu {

        @Test @DisplayName("does not seed when items already exist")
        void skipsWhenItemsExist() {
            when(menuItemRepository.count()).thenReturn(5L);
            menuService.initializeDefaultMenu();
            verify(menuItemRepository, never()).save(any());
        }

        @Test @DisplayName("seeds 12 items when repository is empty")
        void seeds12Items() {
            when(menuItemRepository.count()).thenReturn(0L);
            when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            menuService.initializeDefaultMenu();
            verify(menuItemRepository, times(12)).save(any(MenuItem.class));
        }
    }
}
