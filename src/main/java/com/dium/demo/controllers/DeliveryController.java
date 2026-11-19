package com.dium.demo.controllers;

import com.dium.demo.dto.requests.DeliveryIssueRequest;
import com.dium.demo.dto.requests.HandoverCodeRequest;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.services.delivery.CourierDeliveryService;
import com.dium.demo.services.delivery.CustomerDeliveryService;
import com.dium.demo.services.delivery.DeliveryIssueService;
import com.dium.demo.services.delivery.VenueDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Доставка заказов студентами. Студент (роль CLIENT) — одновременно заказчик и курьер,
 * владелец заведения отдаёт заказ курьеру по коду курьера.
 */
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Tag(name = "Delivery", description = "Доставка заказов студентами-курьерами")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {
    private final CourierDeliveryService courierService;
    private final CustomerDeliveryService customerService;
    private final VenueDeliveryService venueService;
    private final DeliveryIssueService issueService;

    // ---------- курьер ----------

    @Operation(summary = "Свободные заказы для курьера", description = "Свои заказы студент в ленте не видит")
    @PreAuthorize("hasRole('CLIENT')")
    @GetMapping("/available")
    public ResponseEntity<List<DeliveryResponses.Available>> available() {
        return ResponseEntity.ok(courierService.getAvailable());
    }

    @Operation(summary = "Взять заказ", description = "409 — заказ уже взял другой курьер")
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{deliveryId}/accept")
    public ResponseEntity<DeliveryResponses.Courier> accept(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(courierService.accept(deliveryId));
    }

    @Operation(summary = "Отказаться от заказа (только до получения в заведении)")
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{deliveryId}/release")
    public ResponseEntity<DeliveryResponses.Courier> release(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(courierService.release(deliveryId));
    }

    @Operation(summary = "Отдать заказ клиенту",
            description = "Курьер вводит код, который назвал клиент. После исчерпания попыток ввод блокируется "
                    + "и проблема уходит администратору")
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{deliveryId}/deliver")
    public ResponseEntity<DeliveryResponses.Courier> deliver(@PathVariable Long deliveryId,
                                                             @Valid @RequestBody HandoverCodeRequest request) {
        return ResponseEntity.ok(courierService.confirmDelivered(deliveryId, request.code()));
    }

    @Operation(summary = "Мои доставки (как курьер)")
    @PreAuthorize("hasRole('CLIENT')")
    @GetMapping("/courier")
    public ResponseEntity<List<DeliveryResponses.Courier>> courierDeliveries() {
        return ResponseEntity.ok(courierService.getMyDeliveries());
    }

    @Operation(summary = "Заработок курьера")
    @PreAuthorize("hasRole('CLIENT')")
    @GetMapping("/courier/summary")
    public ResponseEntity<DeliveryResponses.CourierSummary> courierSummary() {
        return ResponseEntity.ok(courierService.getSummary());
    }

    // ---------- заказчик ----------

    @Operation(summary = "Доставки моих заказов", description = "Здесь клиент видит курьера и свой код для получения")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/customer")
    public ResponseEntity<List<DeliveryResponses.Customer>> customerDeliveries() {
        return ResponseEntity.ok(customerService.getMyOrderDeliveries());
    }

    // ---------- заведение ----------

    @Operation(summary = "Доставки заказов моего заведения")
    @PreAuthorize("hasRole('VENUE_OWNER')")
    @GetMapping("/venue")
    public ResponseEntity<List<DeliveryResponses.Venue>> venueDeliveries() {
        return ResponseEntity.ok(venueService.getVenueDeliveries());
    }

    @Operation(summary = "Отдать заказ курьеру", description = "Заведение вводит код, который показал курьер")
    @PreAuthorize("hasRole('VENUE_OWNER')")
    @PostMapping("/{deliveryId}/pickup")
    public ResponseEntity<DeliveryResponses.Venue> pickup(@PathVariable Long deliveryId,
                                                          @Valid @RequestBody HandoverCodeRequest request) {
        return ResponseEntity.ok(venueService.confirmPickup(deliveryId, request.code()));
    }

    // ---------- проблемы ----------

    @Operation(summary = "Сообщить о проблеме", description = "Доступно клиенту, курьеру и заведению этой доставки")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{deliveryId}/issues")
    public ResponseEntity<DeliveryResponses.Issue> reportIssue(@PathVariable Long deliveryId,
                                                               @Valid @RequestBody DeliveryIssueRequest request) {
        return ResponseEntity.ok(issueService.report(deliveryId, request));
    }
}
