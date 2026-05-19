package com.hotel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ImportResult {

    private int totalRows;
    private int successCount;
    private int failureCount;
    private int skippedCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public String getSummary() {
        return String.format(
                "Total rows: %d  |  Imported: %d  |  Skipped: %d  |  Errors: %d",
                totalRows, successCount, skippedCount, failureCount);
    }

    public boolean hasErrors() { return failureCount > 0; }
    public boolean isFullSuccess() { return failureCount == 0 && successCount > 0; }
}
