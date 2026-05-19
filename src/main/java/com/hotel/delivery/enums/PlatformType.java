package com.hotel.delivery.enums;

public enum PlatformType {
    ZOMATO("Zomato"),
    SWIGGY("Swiggy"),
    UBER_EATS("Uber Eats"),
    ONDC("ONDC"),
    MOCK("Mock (Testing)");

    private final String displayName;

    PlatformType(String displayName) {
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
