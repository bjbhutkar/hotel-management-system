package com.hotel.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAuthDto {
    private String apiKey;
    private String apiSecret;
    private String restaurantId;
    private String accessToken;
    private String refreshToken;
}
