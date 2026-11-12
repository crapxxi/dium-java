package com.dium.demo.controllers;

import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.services.order.KitchenOrderService;
import com.dium.demo.services.order.OrderService;
import com.dium.demo.services.order.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final KitchenOrderService kitchenOrderService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/user")
    public ResponseEntity<List<OrderResponse>> getUserOrders() {
        return ResponseEntity.ok(orderService.getOrdersByUserId());
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable Long orderId) {
        orderService.updateStatus(orderId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @GetMapping("/kitchen")
    public ResponseEntity<List<OrderResponse>> getKitchenOrders() {
        return ResponseEntity.ok(kitchenOrderService.getKitchenOrders());
    }

    @PreAuthorize("hasRole('VENUE_OWNER')")
    @GetMapping("/order-history")
    public ResponseEntity<List<OrderResponse>> getOrderHistory() {
        return ResponseEntity.ok(kitchenOrderService.getOrderHistory());
    }
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{orderId}/kaspi-url")
    public ResponseEntity<String> getKaspiUrl(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentUrl(orderId));
    }

}
