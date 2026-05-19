package com.hotel.delivery.service;

import com.hotel.delivery.adapter.DeliveryPlatformAdapter;
import com.hotel.delivery.dto.MenuSyncItemDto;
import com.hotel.delivery.entity.DeliveryPlatform;
import com.hotel.delivery.entity.MenuPlatformMapping;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.repository.DeliveryPlatformRepository;
import com.hotel.delivery.repository.MenuPlatformMappingRepository;
import com.hotel.delivery.repository.PlatformCredentialRepository;
import com.hotel.entity.MenuItem;
import com.hotel.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuSyncService {

    private final MenuItemRepository              menuItemRepository;
    private final MenuPlatformMappingRepository   mappingRepository;
    private final DeliveryPlatformRepository      platformRepository;
    private final PlatformCredentialRepository    credentialRepository;
    private final List<DeliveryPlatformAdapter>   adapters;

    private Map<PlatformType, DeliveryPlatformAdapter> adapterMap() {
        return adapters.stream()
                .collect(Collectors.toMap(DeliveryPlatformAdapter::getPlatformType, a -> a));
    }

    public List<MenuPlatformMapping> getMappingsForPlatform(PlatformType platformType) {
        return mappingRepository.findByPlatformType(platformType);
    }

    @Transactional
    public MenuPlatformMapping saveMapping(Long menuItemId, PlatformType platformType,
                                           String platformItemId, String platformCategoryId) {
        Optional<MenuPlatformMapping> existing =
                mappingRepository.findByMenuItemIdAndPlatformType(menuItemId, platformType);

        MenuPlatformMapping mapping = existing.orElseGet(() -> {
            MenuItem mi = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + menuItemId));
            return MenuPlatformMapping.builder()
                    .menuItem(mi)
                    .platformType(platformType)
                    .build();
        });
        mapping.setPlatformItemId(platformItemId);
        mapping.setPlatformCategoryId(platformCategoryId);
        mapping.setSyncEnabled(true);
        return mappingRepository.save(mapping);
    }

    @Transactional
    public String syncFullMenuToPlatform(PlatformType platformType) {
        DeliveryPlatform platform = platformRepository.findByPlatformType(platformType)
                .orElseThrow(() -> new RuntimeException("Platform not configured: " + platformType));
        if (!platform.isActive())
            return "Platform " + platformType + " is not active — enable it first.";

        DeliveryPlatformAdapter adapter = adapterMap().get(platformType);
        if (adapter == null)
            return "No adapter found for " + platformType;

        var credential = credentialRepository.findByPlatformId(platform.getId()).orElse(null);
        List<MenuItem> items = menuItemRepository.findAll();
        List<MenuSyncItemDto> dtos = buildSyncDtos(items, platformType);

        adapter.syncFullMenu(platform, credential, dtos);

        // Record sync timestamp on all mappings
        mappingRepository.findByPlatformType(platformType).forEach(m -> {
            m.setLastSyncedAt(LocalDateTime.now());
            mappingRepository.save(m);
        });

        log.info("Full menu sync to {} — {} items", platformType, dtos.size());
        return "Synced " + dtos.size() + " items to " + platformType.getDisplayName();
    }

    @Transactional
    public void syncSingleItem(Long menuItemId, PlatformType platformType) {
        MenuItem mi = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found: " + menuItemId));
        DeliveryPlatform platform = platformRepository.findByPlatformType(platformType).orElse(null);
        if (platform == null || !platform.isActive()) return;

        DeliveryPlatformAdapter adapter = adapterMap().get(platformType);
        if (adapter == null) return;

        var cred    = credentialRepository.findByPlatformId(platform.getId()).orElse(null);
        var mapping = mappingRepository.findByMenuItemIdAndPlatformType(menuItemId, platformType).orElse(null);
        MenuSyncItemDto dto = toDto(mi, mapping);
        adapter.syncMenuItem(platform, cred, dto);
    }

    public List<MenuItem> getUnmappedItems(PlatformType platformType) {
        List<MenuPlatformMapping> mappings = mappingRepository.findByPlatformType(platformType);
        List<Long> mappedIds = mappings.stream()
                .map(m -> m.getMenuItem().getId()).toList();
        return menuItemRepository.findAll().stream()
                .filter(mi -> !mappedIds.contains(mi.getId()))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<MenuSyncItemDto> buildSyncDtos(List<MenuItem> items, PlatformType platformType) {
        List<MenuPlatformMapping> mappings = mappingRepository.findByPlatformType(platformType);
        Map<Long, MenuPlatformMapping> mappingByItemId = mappings.stream()
                .collect(Collectors.toMap(m -> m.getMenuItem().getId(), m -> m));

        List<MenuSyncItemDto> result = new ArrayList<>();
        for (MenuItem mi : items) {
            MenuPlatformMapping mapping = mappingByItemId.get(mi.getId());
            result.add(toDto(mi, mapping));
        }
        return result;
    }

    private MenuSyncItemDto toDto(MenuItem mi, MenuPlatformMapping mapping) {
        return MenuSyncItemDto.builder()
                .internalMenuItemId(mi.getId())
                .platformItemId(mapping != null ? mapping.getPlatformItemId() : null)
                .platformCategoryId(mapping != null ? mapping.getPlatformCategoryId() : null)
                .name(mi.getName())
                .description(mi.getDescription())
                .category(mi.getCategory())
                .price(mi.getPrice())
                .available(mi.isAvailable())
                .build();
    }
}
