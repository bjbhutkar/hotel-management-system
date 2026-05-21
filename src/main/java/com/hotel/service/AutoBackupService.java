package com.hotel.service;

import com.hotel.entity.BackupConfig;
import com.hotel.entity.BackupFrequency;
import com.hotel.repository.BackupConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoBackupService {

    private final BackupConfigRepository backupConfigRepository;
    private final BackupService backupService;

    @Transactional
    public BackupConfig getConfig() {
        return backupConfigRepository.findById(1L).orElseGet(() -> {
            BackupConfig defaultConfig = BackupConfig.builder()
                    .id(1L)
                    .enabled(false)
                    .frequency(BackupFrequency.DISABLED)
                    .backupDirectory(System.getProperty("user.home") + "/hotel_management/backups")
                    .retainCount(10)
                    .build();
            return backupConfigRepository.save(defaultConfig);
        });
    }

    @Transactional
    public BackupConfig saveConfig(BackupConfig config) {
        if (Boolean.TRUE.equals(config.getEnabled()) && config.getFrequency() != BackupFrequency.DISABLED) {
            if (config.getNextBackupAt() == null) {
                config.setNextBackupAt(computeNextBackupTime(LocalDateTime.now(), config.getFrequency()));
            }
        } else {
            config.setNextBackupAt(null);
        }
        return backupConfigRepository.save(config);
    }

    @Transactional
    public String performManualBackup() throws IOException {
        BackupConfig config = getConfig();
        String dir = resolveBackupDirectory(config);
        String backupPath = backupService.createBackup(dir);
        config.setLastBackupAt(LocalDateTime.now());
        backupConfigRepository.save(config);
        cleanOldBackups(dir, config.getRetainCount() != null ? config.getRetainCount() : 10);
        log.info("Manual backup created: {}", backupPath);
        return backupPath;
    }

    @Transactional
    public void restoreFromFile(String backupFilePath) throws IOException {
        backupService.restoreBackup(backupFilePath);
        log.info("Database restored from: {}", backupFilePath);
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void checkAndRunScheduledBackup() {
        Optional<BackupConfig> optConfig = backupConfigRepository.findById(1L);
        if (optConfig.isEmpty()) return;

        BackupConfig config = optConfig.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) return;
        if (config.getFrequency() == null || config.getFrequency() == BackupFrequency.DISABLED) return;
        if (config.getBackupDirectory() == null || config.getBackupDirectory().isBlank()) return;
        if (config.getNextBackupAt() == null || LocalDateTime.now().isBefore(config.getNextBackupAt())) return;

        try {
            String path = backupService.createBackup(config.getBackupDirectory());
            log.info("Scheduled backup created: {}", path);
            config.setLastBackupAt(LocalDateTime.now());
            config.setNextBackupAt(computeNextBackupTime(LocalDateTime.now(), config.getFrequency()));
            backupConfigRepository.save(config);
            cleanOldBackups(config.getBackupDirectory(), config.getRetainCount() != null ? config.getRetainCount() : 10);
        } catch (IOException e) {
            log.error("Scheduled backup failed: {}", e.getMessage(), e);
        }
    }

    private LocalDateTime computeNextBackupTime(LocalDateTime from, BackupFrequency freq) {
        return switch (freq) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case SIX_MONTHLY -> from.plusMonths(6);
            case ANNUALLY -> from.plusYears(1);
            default -> null;
        };
    }

    private String resolveBackupDirectory(BackupConfig config) {
        String dir = config.getBackupDirectory();
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("user.home") + "/hotel_management/backups";
        }
        return dir;
    }

    private void cleanOldBackups(String directory, int retainCount) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.exists(dir)) return;
            File[] backupFiles = dir.toFile().listFiles(
                    (d, name) -> name.startsWith("hotel_backup_") && name.endsWith(".db"));
            if (backupFiles == null || backupFiles.length <= retainCount) return;
            Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));
            int toDelete = backupFiles.length - retainCount;
            for (int i = 0; i < toDelete; i++) {
                if (backupFiles[i].delete()) {
                    log.info("Old backup deleted: {}", backupFiles[i].getName());
                }
            }
        } catch (Exception e) {
            log.warn("Could not clean old backups: {}", e.getMessage());
        }
    }
}
