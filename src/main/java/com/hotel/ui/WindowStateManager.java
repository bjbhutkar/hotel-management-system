package com.hotel.ui;

/**
 * Pure-Java decision logic for primary-stage sizing after a scene swap.
 * No JavaFX imports — fully unit-testable without a running toolkit.
 */
public class WindowStateManager {

    public enum Action {
        /** Center on screen at default dimensions (used for any login transition). */
        CENTER,
        /** Restore the exact width/height/x/y that were saved before the swap. */
        RESTORE,
        /** Re-maximize the window (it was maximized before the swap). */
        MAXIMIZE
    }

    public record Plan(Action action, double width, double height, double x, double y) {

        public static Plan center() {
            return new Plan(Action.CENTER, 0, 0, 0, 0);
        }

        public static Plan maximize() {
            return new Plan(Action.MAXIMIZE, 0, 0, 0, 0);
        }

        public static Plan restore(double w, double h, double x, double y) {
            return new Plan(Action.RESTORE, w, h, x, y);
        }
    }

    /**
     * Decides what to do with the stage after a scene swap.
     *
     * @param prevResizable was the outgoing scene resizable?
     * @param nextResizable will the incoming scene be resizable?
     * @param wasMaximized  was the window maximized before the swap?
     * @param savedW        window width captured before the swap (only valid when !wasMaximized)
     * @param savedH        window height captured before the swap
     * @param savedX        window X position captured before the swap
     * @param savedY        window Y position captured before the swap
     */
    public Plan plan(boolean prevResizable, boolean nextResizable, boolean wasMaximized,
                     double savedW, double savedH, double savedX, double savedY) {

        // Non-resizable target (e.g. login screen) → always center at its default size.
        if (!nextResizable) {
            return Plan.center();
        }

        // Transitioning FROM a fixed-size screen (login) TO a resizable screen → maximize.
        // This implements the "launch in maximized mode" default for the dashboard and all
        // other work screens. The login screen itself stays small and centered.
        if (!prevResizable) {
            return Plan.maximize();
        }

        // Both scenes are resizable: honour whatever state the window was already in.
        if (wasMaximized) {
            return Plan.maximize();
        }

        // Both resizable, normal (un-maximized) size → restore exact dimensions/position.
        return Plan.restore(savedW, savedH, savedX, savedY);
    }
}
