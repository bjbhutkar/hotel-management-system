package com.hotel.service;

import com.hotel.entity.MenuItem;
import com.hotel.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository menuItemRepository;

    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }

    public List<MenuItem> getAvailableMenuItems() {
        return menuItemRepository.findByAvailableTrue();
    }

    public List<MenuItem> getMenuItemsByCategory(String category) {
        return menuItemRepository.findByCategoryAndAvailableTrue(category);
    }

    public List<String> getAllCategories() {
        return menuItemRepository.findAllCategories();
    }

    public Optional<MenuItem> getMenuItemById(Long id) {
        return menuItemRepository.findById(id);
    }

    @Transactional
    public MenuItem addMenuItem(String name, String description, String category, BigDecimal price) {
        MenuItem item = MenuItem.builder()
                .name(name)
                .description(description)
                .category(category)
                .price(price)
                .available(true)
                .build();
        return menuItemRepository.save(item);
    }

    @Transactional
    public MenuItem updateMenuItem(Long id, String name, String description,
                                   String category, BigDecimal price, boolean available) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found: " + id));
        item.setName(name);
        item.setDescription(description);
        item.setCategory(category);
        item.setPrice(price);
        item.setAvailable(available);
        return menuItemRepository.save(item);
    }

    @Transactional
    public void deleteMenuItem(Long id) {
        menuItemRepository.deleteById(id);
        log.info("Deleted menu item id={}", id);
    }

    @Transactional
    public void toggleAvailability(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found: " + id));
        item.setAvailable(!item.isAvailable());
        menuItemRepository.save(item);
    }

    /** Seeds a starter menu on first run. */
    @Transactional
    public void initializeDefaultMenu() {
        if (menuItemRepository.count() > 0) return;

        Object[][] defaults = {
            {"Butter Chicken",   "Classic North Indian curry",        "Main Course", "280.00"},
            {"Paneer Tikka",     "Grilled cottage cheese",            "Starters",    "220.00"},
            {"Chicken Biryani",  "Fragrant basmati rice with chicken","Main Course", "260.00"},
            {"Dal Tadka",        "Yellow lentils with tadka",         "Main Course", "160.00"},
            {"Naan",             "Soft tandoor bread",                "Breads",      "40.00"},
            {"Roti",             "Whole wheat bread",                 "Breads",      "25.00"},
            {"Masala Chai",      "Spiced Indian tea",                 "Beverages",   "30.00"},
            {"Cold Coffee",      "Chilled coffee with ice cream",     "Beverages",   "80.00"},
            {"Gulab Jamun",      "Sweet milk-solid dumplings",        "Desserts",    "60.00"},
            {"Ice Cream",        "Vanilla / Chocolate / Strawberry",  "Desserts",    "70.00"},
            {"Veg Soup",         "Fresh garden vegetable soup",       "Starters",    "90.00"},
            {"French Fries",     "Crispy golden fries",               "Starters",    "100.00"},
        };

        for (Object[] row : defaults) {
            addMenuItem((String) row[0], (String) row[1],
                        (String) row[2], new BigDecimal((String) row[3]));
        }
        log.info("Default menu items seeded.");
    }
}
