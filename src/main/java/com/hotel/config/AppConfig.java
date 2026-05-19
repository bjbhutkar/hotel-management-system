package com.hotel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.File;

/**
 * Spring application configuration.
 * Registers BCrypt encoder and ensures the database directory exists.
 */
@Configuration
public class AppConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Ensure the SQLite database directory exists before Hibernate tries to create the file.
     */
    @Bean
    public Boolean ensureDatabaseDirectory() {
        File dir = new File(System.getProperty("user.home"), "hotel_management");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return Boolean.TRUE;
    }
}
