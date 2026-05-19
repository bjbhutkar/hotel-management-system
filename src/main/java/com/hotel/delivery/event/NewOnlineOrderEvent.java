package com.hotel.delivery.event;

import com.hotel.delivery.entity.OnlineOrder;
import org.springframework.context.ApplicationEvent;

public class NewOnlineOrderEvent extends ApplicationEvent {

    private final OnlineOrder order;

    public NewOnlineOrderEvent(Object source, OnlineOrder order) {
        super(source);
        this.order = order;
    }

    public OnlineOrder getOrder() {
        return order;
    }
}
