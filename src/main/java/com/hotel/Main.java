package com.hotel;

/**
 * Launcher class — needed so the fat JAR manifest points here,
 * avoiding the "JavaFX runtime components are missing" error when
 * the main class extends Application directly.
 */
public class Main {
    public static void main(String[] args) {
        HotelManagementApp.main(args);
    }
}
