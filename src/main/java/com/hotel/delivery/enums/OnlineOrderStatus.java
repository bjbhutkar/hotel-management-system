package com.hotel.delivery.enums;

public enum OnlineOrderStatus {
    NEW("New"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    PREPARING("Preparing"),
    READY("Ready"),
    PICKED_UP("Picked Up"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    private final String displayName;

    OnlineOrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REJECTED;
    }

    public boolean isActionable() {
        return this == NEW;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
