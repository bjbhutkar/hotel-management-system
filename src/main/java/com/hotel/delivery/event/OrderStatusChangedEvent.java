package com.hotel.delivery.event;

import com.hotel.delivery.entity.OnlineOrder;
import com.hotel.delivery.enums.OnlineOrderStatus;
import org.springframework.context.ApplicationEvent;

public class OrderStatusChangedEvent extends ApplicationEvent {

    private final OnlineOrder order;
    private final OnlineOrderStatus previousStatus;
    private final OnlineOrderStatus newStatus;

    public OrderStatusChangedEvent(Object source, OnlineOrder order,
                                   OnlineOrderStatus previousStatus, OnlineOrderStatus newStatus) {
        super(source);
        this.order = order;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public OnlineOrder getOrder()                   { return order; }
    public OnlineOrderStatus getPreviousStatus()    { return previousStatus; }
    public OnlineOrderStatus getNewStatus()         { return newStatus; }
}
