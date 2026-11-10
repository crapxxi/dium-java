package com.dium.demo.services.order;

import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderRequest request);
    void updateStatus(Long orderId);
    void cancelOrder(Long orderId);
    List<OrderResponse> getOrdersByUserId();
}