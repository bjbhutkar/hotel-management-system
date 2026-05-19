package com.hotel.ui;

import com.hotel.entity.Order;
import com.hotel.service.BillingService;
import com.hotel.service.OrderService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class BillingController {

    @FXML private Label    orderNumberLabel;
    @FXML private Label    tableNumberLabel;
    @FXML private Label    totalAmountLabel;
    @FXML private TextArea invoiceTextArea;
    @FXML private Button   printButton;
    @FXML private Button   saveButton;
    @FXML private Button   closeButton;

    private final BillingService billingService;
    private final OrderService   orderService;

    private Order order;

    /** Called by StageManager after the window opens. */
    public void loadOrder(Long orderId) {
        this.order = orderService.getOrderById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        orderNumberLabel.setText(order.getOrderNumber());
        tableNumberLabel.setText("Table " + order.getTableNumber());
        totalAmountLabel.setText("₹" + order.getTotalAmount().setScale(2));
        invoiceTextArea.setText(billingService.generateInvoiceText(order));
        invoiceTextArea.setStyle("-fx-font-family: monospace; -fx-font-size: 13;");
    }

    @FXML
    public void handlePrint() {
        billingService.printInvoice(order);
        // Refresh order reference after billing
        order = orderService.getOrderById(order.getId()).orElse(order);
        new Alert(Alert.AlertType.INFORMATION,
                "Invoice generated!\nOrder " + order.getOrderNumber() + " marked as billed.",
                ButtonType.OK).showAndWait();
        closeWindow();
    }

    @FXML
    public void handleSaveToFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Invoice");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
        fc.setInitialFileName(order.getOrderNumber() + "_invoice.txt");

        File file = fc.showSaveDialog(saveButton.getScene().getWindow());
        if (file != null) {
            try {
                billingService.saveInvoiceToFile(order, file.getAbsolutePath());
                new Alert(Alert.AlertType.INFORMATION,
                        "Invoice saved to:\n" + file.getAbsolutePath(),
                        ButtonType.OK).showAndWait();
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR,
                        "Error saving file:\n" + e.getMessage(),
                        ButtonType.OK).showAndWait();
            }
        }
    }

    @FXML
    public void handleClose() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) closeButton.getScene().getWindow()).close();
    }
}
