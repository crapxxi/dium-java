package com.dium.demo.controllers;

import com.dium.demo.dto.order.CreateOrderRequest;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
@Tag(name = "Order")
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "create order")
    public ResponseEntity<?> create(@AuthenticationPrincipal UserDetails userDetails, @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(userDetails, request));
    }

    @GetMapping("/user")
    @Operation(summary = "get user's orders")
    public ResponseEntity<?> getUserOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(orderService.getOrdersByUserId(userDetails));
    }

    @PatchMapping("{orderId}/status")
    @Operation(summary = "update status")
    public ResponseEntity<?> updateStatus(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long orderId,
                                          @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(userDetails, orderId, status));
    }

    @GetMapping("/kitchen")
    @Operation(summary = "get kitchen orders")
    public ResponseEntity<?> getKitchenOrders(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(orderService.getKitchenOrders(userDetails));
    }
    @GetMapping("/order-history")
    @Operation(summary = "get venue order history")
    public ResponseEntity<?> getOrderHistory(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(orderService.getOrderHistory(userDetails));
    }
    @GetMapping("/{orderId}/kaspi-url")
    @Operation(summary = "get kaspi url of venue")
    public ResponseEntity<?> getKaspiUrl(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getKaspiUrl(orderId));
    }

}
