package com.hotel.delivery.dto;

import com.hotel.delivery.enums.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomingOrderDto {

    private String platformOrderId;
    private PlatformType platformType;

    // Customer
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private String customerNotes;

    // Items
    @Builder.Default
    private List<IncomingOrderItemDto> items = new ArrayList<>();

    // Financials
    private BigDecimal itemsTotal;
    private BigDecimal taxAmount;
    private BigDecimal deliveryCharge;
    private BigDecimal discount;
    private BigDecimal grandTotal;
    private String paymentMode;
    private boolean prepaid;

    // Delivery partner
    private String deliveryPartnerName;
    private String deliveryPartnerPhone;

    // Timing
    private LocalDateTime placedAt;

    // Raw platform payload (for debugging / audit)
    private String rawPayload;
}
