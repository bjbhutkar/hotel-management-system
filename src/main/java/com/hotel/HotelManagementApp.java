package com.hotel;

import com.hotel.ui.StageManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main JavaFX Application class.
 * Bootstraps the Spring context in init(), then hands off to StageManager.
 */
@SpringBootApplication
public class HotelManagementApp extends Application {

    private static ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = SpringApplication.run(HotelManagementApp.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        StageManager stageManager = springContext.getBean(StageManager.class);
        stageManager.setPrimaryStage(primaryStage);
        stageManager.showLoginScreen();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }

    public static ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
