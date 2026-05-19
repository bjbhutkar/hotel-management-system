package com.hotel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class BackupService {

    @Value("${app.database.path:${user.home}/hotel_management/hotel_management.db}")
    private String databasePath;

    /**
     * Copies the SQLite database to the specified backup directory.
     *
     * @param backupDirectory Target directory path
     * @return Full path to the created backup file
     */
    public String createBackup(String backupDirectory) throws IOException {
        Path source = Paths.get(databasePath);
        if (!Files.exists(source)) {
            throw new FileNotFoundException("Database not found at: " + databasePath);
        }

        String timestamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFile = "hotel_backup_" + timestamp + ".db";

        Path backupDir  = Paths.get(backupDirectory);
        Files.createDirectories(backupDir);

        Path destination = backupDir.resolve(backupFile);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

        log.info("Backup created: {}", destination.toAbsolutePath());
        return destination.toAbsolutePath().toString();
    }

    /**
     * Restores the database from a backup file.
     * A safety copy (.before_restore) is kept in the same directory.
     *
     * @param backupFilePath Full path to the .db backup file
     */
    public void restoreBackup(String backupFilePath) throws IOException {
        Path backup = Paths.get(backupFilePath);
        if (!Files.exists(backup)) {
            throw new FileNotFoundException("Backup file not found: " + backupFilePath);
        }

        Path destination = Paths.get(databasePath);

        // Save a safety copy before overwriting
        if (Files.exists(destination)) {
            Path safetyCopy = destination.resolveSibling("hotel_management.before_restore.db");
            Files.copy(destination, safetyCopy, StandardCopyOption.REPLACE_EXISTING);
            log.info("Safety copy saved to {}", safetyCopy);
        }

        Files.copy(backup, destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Database restored from {}", backupFilePath);
    }

    public String getDatabasePath() {
        return databasePath;
    }
}
