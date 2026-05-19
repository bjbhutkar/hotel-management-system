package com.hotel.config;

import com.hotel.service.MenuService;
import com.hotel.service.RestaurantConfigService;
import com.hotel.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs on startup to seed the database with default users, menu items,
 * and restaurant configuration if they don't already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserService             userService;
    private final MenuService             menuService;
    private final RestaurantConfigService restaurantConfigService;

    @Override
    public void run(String... args) {
        log.info("Initializing default data...");
        userService.initializeDefaultUsers();
        menuService.initializeDefaultMenu();
        restaurantConfigService.initializeDefaultConfig();
        log.info("Data initialization complete.");
    }
}
