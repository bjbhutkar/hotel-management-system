package com.hotel.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Setter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Centrally manages JavaFX scene transitions so controllers
 * never need to hold a reference to Stage themselves.
 */
@Component
public class StageManager {

    private final ApplicationContext appContext;
    final WindowStateManager windowStateManager = new WindowStateManager();

    @Setter
    private Stage primaryStage;

    public StageManager(ApplicationContext appContext) {
        this.appContext = appContext;
    }

    public void showLoginScreen() throws IOException {
        applyAppIcon();
        switchScene("/fxml/login.fxml", "Rasoi — Login", 520, 650, false);
    }

    public void showDashboard() throws IOException {
        switchScene("/fxml/dashboard.fxml", "Rasoi — Dashboard", 1200, 750, true);
    }

    public void showMenuManagement() throws IOException {
        switchScene("/fxml/menu-management.fxml", "Rasoi — Menu Management", 950, 680, true);
    }

    public void showOrderManagement() throws IOException {
        switchScene("/fxml/order-management.fxml", "Rasoi — Order Management", 1150, 720, true);
    }

    public void showReports() throws IOException {
        switchScene("/fxml/reports.fxml", "Rasoi — Reports & Analytics", 1000, 720, true);
    }

    public void showOnlineOrdersDashboard() throws IOException {
        switchScene("/fxml/online-orders-dashboard.fxml", "Rasoi — Online Orders", 1280, 780, true);
    }

    public void showPlatformConfig() throws IOException {
        switchScene("/fxml/platform-config.fxml", "Rasoi — Platform Configuration", 820, 640, true);
    }

    public void showMenuSync() throws IOException {
        switchScene("/fxml/menu-sync.fxml", "Rasoi — Menu Sync", 1000, 700, true);
    }

    public void showSettings() throws IOException {
        switchScene("/fxml/settings.fxml", "Rasoi — Settings", 1100, 720, true);
    }

    public void showImportData() throws IOException {
        switchScene("/fxml/import-data.fxml", "Rasoi — Import Data", 1000, 760, true);
    }

    public void showBilling(Long orderId) throws IOException {
        FXMLLoader loader = createLoader("/fxml/billing.fxml");
        Stage billingStage = new Stage();
        billingStage.initModality(Modality.APPLICATION_MODAL);
        billingStage.initOwner(primaryStage);
        billingStage.setTitle("Rasoi — Billing & Invoice");
        applyIconTo(billingStage);

        Scene scene = new Scene(loader.load(), 720, 620);
        scene.getStylesheets().add(cssUrl());
        billingStage.setScene(scene);
        billingStage.setResizable(false);

        BillingController controller = loader.getController();
        controller.loadOrder(orderId);
        billingStage.showAndWait();
    }

    // -----------------------------------------------------------------------

    private void switchScene(String fxmlPath, String title,
                              double defaultWidth, double defaultHeight,
                              boolean resizable) throws IOException {

        boolean prevResizable = primaryStage.isResizable();
        boolean wasMaximized  = primaryStage.isMaximized();

        // Capture the restored (non-maximized) dimensions BEFORE any state change.
        // When the window is maximized, getWidth/Height/X/Y return screen-edge values
        // that must not be re-applied as a normal window position after a scene swap.
        double savedW = wasMaximized ? defaultWidth  : primaryStage.getWidth();
        double savedH = wasMaximized ? defaultHeight : primaryStage.getHeight();
        double savedX = wasMaximized ? 0             : primaryStage.getX();
        double savedY = wasMaximized ? 0             : primaryStage.getY();

        // Always unmaximize BEFORE swapping the scene.
        // On Windows, calling setScene() on a maximized Stage silently resets the
        // window position to (0, 0) / a screen corner. Pre-unmaximizing puts the
        // window in a predictable non-maximized state so our subsequent size/position
        // logic is applied to a sane baseline.
        if (wasMaximized) {
            primaryStage.setMaximized(false);
        }

        FXMLLoader loader = createLoader(fxmlPath);
        Scene scene = new Scene(loader.load(), defaultWidth, defaultHeight);
        scene.getStylesheets().add(cssUrl());

        primaryStage.setTitle(title);
        primaryStage.setScene(scene);
        primaryStage.setResizable(resizable);

        WindowStateManager.Plan plan = windowStateManager.plan(
                prevResizable, resizable, wasMaximized,
                savedW, savedH, savedX, savedY);

        switch (plan.action()) {
            case CENTER  -> primaryStage.centerOnScreen();
            case RESTORE -> {
                primaryStage.setWidth(plan.width());
                primaryStage.setHeight(plan.height());
                primaryStage.setX(plan.x());
                primaryStage.setY(plan.y());
            }
            case MAXIMIZE -> { /* handled after show() below */ }
        }

        primaryStage.show();

        // Re-maximize AFTER show() so the platform has a fully realised window handle.
        // Platform.runLater guarantees this runs after the layout pass, preventing a
        // one-frame flash at the wrong size on Windows.
        if (plan.action() == WindowStateManager.Action.MAXIMIZE) {
            Platform.runLater(() -> primaryStage.setMaximized(true));
        }
    }

    // -----------------------------------------------------------------------

    private void applyAppIcon() {
        if (primaryStage.getIcons().isEmpty()) {
            applyIconTo(primaryStage);
        }
    }

    private void applyIconTo(Stage stage) {
        try (InputStream is = getClass().getResourceAsStream("/images/rasoi.png")) {
            if (is != null) {
                stage.getIcons().add(new Image(is));
            }
        } catch (Exception ignored) {}
    }

    private FXMLLoader createLoader(String fxmlPath) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.setControllerFactory(appContext::getBean);
        return loader;
    }

    private String cssUrl() {
        return getClass().getResource("/css/styles.css").toExternalForm();
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }
}
