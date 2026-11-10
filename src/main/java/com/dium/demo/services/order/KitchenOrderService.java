package com.dium.demo.services.order;

import com.dium.demo.dto.responses.OrderResponse;

import java.util.List;

public interface KitchenOrderService {
    List<OrderResponse> getOrderHistory();
    List<OrderResponse> getKitchenOrders();
}
