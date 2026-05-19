package com.hotel.service;

import com.hotel.entity.RestaurantConfig;
import com.hotel.repository.RestaurantConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Manages the single restaurant configuration row.
 *
 * The config is loaded once and cached in memory; `saveConfig()` flushes both
 * the DB and the cache atomically. This makes reads O(1) after the first call
 * without requiring Spring caching infrastructure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantConfigService {

    private final RestaurantConfigRepository repository;

    private volatile RestaurantConfig cachedConfig;

    // ── Read ──────────────────────────────────────────────────────────────────

    public synchronized RestaurantConfig getConfig() {
        if (cachedConfig == null) {
            cachedConfig = repository.findById(1L).orElseGet(this::buildDefault);
        }
        return cachedConfig;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public synchronized RestaurantConfig saveConfig(RestaurantConfig config) {
        config.setId(1L);
        cachedConfig = repository.save(config);
        log.info("Restaurant configuration saved: {}", cachedConfig.getRestaurantName());
        return cachedConfig;
    }

    /** Initializes the default config row on first startup. */
    @Transactional
    public void initializeDefaultConfig() {
        if (repository.count() == 0) {
            RestaurantConfig def = buildDefault();
            repository.save(def);
            cachedConfig = def;
            log.info("Default restaurant configuration created");
        }
    }

    // ── Logo storage ──────────────────────────────────────────────────────────

    /**
     * Copies the user-chosen image to the app's logo storage directory.
     * Returns the canonical path of the stored file.
     */
    public String copyLogoToStorage(File sourceFile) throws IOException {
        Path storageDir = Paths.get(getLogoStorageDir());
        Files.createDirectories(storageDir);

        String ext      = getExtension(sourceFile.getName());
        Path   destPath = storageDir.resolve("logo" + ext);
        Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Logo stored at {}", destPath);
        return destPath.toAbsolutePath().toString();
    }

    public void removeLogo() {
        RestaurantConfig cfg = getConfig();
        if (cfg.getLogoPath() != null) {
            try { Files.deleteIfExists(Paths.get(cfg.getLogoPath())); } catch (Exception ignored) {}
            cfg.setLogoPath(null);
            saveConfig(cfg);
        }
    }

    public String getLogoStorageDir() {
        return System.getProperty("user.home") + "/hotel_management/logos/";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RestaurantConfig buildDefault() {
        return RestaurantConfig.builder()
                .id(1L)
                .restaurantName("Rasoi Restaurant")
                .currencySymbol("₹")
                .invoicePrefix("INV")
                .thankYouMessage("Thank you for dining with us!")
                .footerMessage("Please visit us again!")
                .build();
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".png";
    }
}
