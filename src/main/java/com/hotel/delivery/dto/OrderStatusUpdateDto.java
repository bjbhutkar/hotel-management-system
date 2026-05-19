package com.hotel.delivery.dto;

import com.hotel.delivery.enums.OnlineOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusUpdateDto {
    private String platformOrderId;
    private OnlineOrderStatus newStatus;
    private String reason;
    private Integer prepTimeMinutes;
}
