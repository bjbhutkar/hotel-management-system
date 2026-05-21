package com.hotel.entity;

public enum BackupFrequency {
    DISABLED("Disabled"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    SIX_MONTHLY("Every 6 Months"),
    ANNUALLY("Annually");

    private final String displayName;

    BackupFrequency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
