package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "BACKUP_CONFIG")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupConfig {

    @Id
    @Builder.Default
    private Long id = 1L;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private BackupFrequency frequency = BackupFrequency.DISABLED;

    @Column(length = 500)
    private String backupDirectory;

    @Column
    private LocalDateTime lastBackupAt;

    @Column
    private LocalDateTime nextBackupAt;

    @Column
    @Builder.Default
    private Integer retainCount = 10;

    @Column
    @Builder.Default
    private Boolean enabled = false;
}
