package com.hotel.delivery.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.repository.DeliveryPlatformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Delivery module configuration.
 *
 * - Enables Spring scheduling (required for platform polling).
 * - Seeds the DELIVERY_PLATFORMS table with all known platforms on first startup.
 * - Provides a shared Jackson ObjectMapper for all adapters.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DeliveryConfig {

    private final DeliveryPlatformRepository platformRepository;

    @Bean
    public ObjectMapper deliveryObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public ApplicationRunner seedDeliveryPlatforms() {
        return args -> {
            seedPlatformIfAbsent(PlatformType.ZOMATO, "Zomato");
            seedPlatformIfAbsent(PlatformType.SWIGGY, "Swiggy");
            seedPlatformIfAbsent(PlatformType.MOCK,   "Mock (Testing)");
            log.info("Delivery platforms seeded");
        };
    }

    private void seedPlatformIfAbsent(PlatformType type, String displayName) {
        if (platformRepository.findByPlatformType(type).isEmpty()) {
            platformRepository.save(DeliveryPlatform.builder()
                    .platformType(type)
                    .displayName(displayName)
                    .active(false)
                    .pollIntervalSeconds(30)
                    .build());
        }
    }
}
