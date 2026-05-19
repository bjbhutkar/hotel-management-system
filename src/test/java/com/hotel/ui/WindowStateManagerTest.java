package com.hotel.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WindowStateManager — the pure logic that decides how the
 * primary stage should be sized/positioned after every scene transition.
 *
 * No JavaFX toolkit needed; the class has zero JavaFX imports.
 */
class WindowStateManagerTest {

    private WindowStateManager manager;

    // Readability aliases
    private static final boolean RESIZABLE     = true;
    private static final boolean NON_RESIZABLE = false;
    private static final boolean MAXIMIZED     = true;
    private static final boolean NORMAL        = false;

    @BeforeEach
    void setUp() {
        manager = new WindowStateManager();
    }

    // -----------------------------------------------------------------------
    // Transitions that involve the login screen (non-resizable)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Login-screen transitions")
    class LoginTransitions {

        @Test
        @DisplayName("Login → Dashboard: MAXIMIZE (launch-in-maximized-mode default)")
        void loginToDashboard() {
            var plan = manager.plan(NON_RESIZABLE, RESIZABLE, NORMAL, 1200, 750, 100, 80);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Dashboard → Login: CENTER (login is fixed-size)")
        void dashboardToLogin() {
            var plan = manager.plan(RESIZABLE, NON_RESIZABLE, NORMAL, 1200, 750, 100, 80);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.CENTER);
        }

        @Test
        @DisplayName("Login → Dashboard (wasMaximized flag set): still MAXIMIZE")
        void loginMaximized_toDashboard_maximize() {
            // wasMaximized flag on login is meaningless — non-resizable → resizable always maximizes
            var plan = manager.plan(NON_RESIZABLE, RESIZABLE, MAXIMIZED, 640, 420, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Order Management → Login: CENTER")
        void orderManagementToLogin() {
            var plan = manager.plan(RESIZABLE, NON_RESIZABLE, NORMAL, 1150, 720, 50, 60);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.CENTER);
        }
    }

    // -----------------------------------------------------------------------
    // Resizable → Resizable while maximized
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Maximized resizable → resizable transitions → MAXIMIZE")
    class MaximizedTransitions {

        @Test
        @DisplayName("Dashboard (maximized) → Order Management: MAXIMIZE")
        void dashboardMaximized_toOrderManagement() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, MAXIMIZED, 1200, 750, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Dashboard (maximized) → Menu Management: MAXIMIZE")
        void dashboardMaximized_toMenuManagement() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, MAXIMIZED, 1200, 750, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Dashboard (maximized) → Reports: MAXIMIZE")
        void dashboardMaximized_toReports() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, MAXIMIZED, 1200, 750, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Order Management (maximized) → Dashboard: MAXIMIZE")
        void orderManagementMaximized_toDashboard() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, MAXIMIZED, 1150, 720, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Reports (maximized) → Order Management: MAXIMIZE")
        void reportsMaximized_toOrderManagement() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, MAXIMIZED, 1000, 720, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("MAXIMIZE plan has no meaningful size/position data")
        void maximizePlan_carriesZeroCoordinates() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, MAXIMIZED, 999, 888, 77, 55);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.MAXIMIZE);
            // The coordinates are irrelevant — MAXIMIZE ignores them
            assertThat(plan.width()).isZero();
            assertThat(plan.height()).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // Resizable → Resizable at normal (non-maximized) size
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Normal-size resizable → resizable transitions → RESTORE")
    class RestoreTransitions {

        @Test
        @DisplayName("Dashboard (normal) → Order Management: RESTORE with correct size")
        void dashboardNormal_toOrderManagement_restoresSize() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, NORMAL, 1100, 700, 200, 150);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.RESTORE);
            assertThat(plan.width()).isEqualTo(1100);
            assertThat(plan.height()).isEqualTo(700);
        }

        @Test
        @DisplayName("Dashboard (normal) → Order Management: RESTORE with correct position")
        void dashboardNormal_toOrderManagement_restoresPosition() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, NORMAL, 1100, 700, 200, 150);
            assertThat(plan.x()).isEqualTo(200);
            assertThat(plan.y()).isEqualTo(150);
        }

        @Test
        @DisplayName("Order Management (normal) → Reports: RESTORE")
        void orderManagementNormal_toReports() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, NORMAL, 950, 680, 50, 80);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.RESTORE);
        }

        @Test
        @DisplayName("Window at origin (0,0): RESTORE preserves origin position")
        void windowAtOrigin_restoresOrigin() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, NORMAL, 1200, 750, 0, 0);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.RESTORE);
            assertThat(plan.x()).isZero();
            assertThat(plan.y()).isZero();
        }

        @Test
        @DisplayName("Large non-standard window size is preserved exactly")
        void largeCustomSize_preserved() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, NORMAL, 1920, 1080, 0, 0);
            assertThat(plan.width()).isEqualTo(1920);
            assertThat(plan.height()).isEqualTo(1080);
        }

        @Test
        @DisplayName("Negative X (multi-monitor left) is preserved")
        void negativeX_preserved() {
            var plan = manager.plan(RESIZABLE, RESIZABLE, NORMAL, 1280, 800, -1920, 100);
            assertThat(plan.action()).isEqualTo(WindowStateManager.Action.RESTORE);
            assertThat(plan.x()).isEqualTo(-1920);
        }
    }

    // -----------------------------------------------------------------------
    // Factory method sanity checks
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Plan factory methods")
    class PlanFactories {

        @Test
        @DisplayName("Plan.center() returns CENTER action")
        void planCenter() {
            assertThat(WindowStateManager.Plan.center().action())
                    .isEqualTo(WindowStateManager.Action.CENTER);
        }

        @Test
        @DisplayName("Plan.maximize() returns MAXIMIZE action")
        void planMaximize() {
            assertThat(WindowStateManager.Plan.maximize().action())
                    .isEqualTo(WindowStateManager.Action.MAXIMIZE);
        }

        @Test
        @DisplayName("Plan.restore() returns RESTORE action with correct values")
        void planRestore() {
            var p = WindowStateManager.Plan.restore(800, 600, 10, 20);
            assertThat(p.action()).isEqualTo(WindowStateManager.Action.RESTORE);
            assertThat(p.width()).isEqualTo(800);
            assertThat(p.height()).isEqualTo(600);
            assertThat(p.x()).isEqualTo(10);
            assertThat(p.y()).isEqualTo(20);
        }
    }
}
