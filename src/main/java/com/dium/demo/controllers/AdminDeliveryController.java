package com.dium.demo.controllers;

import com.dium.demo.dto.requests.AdminDeliveryActionRequest;
import com.dium.demo.dto.requests.ResolveIssueRequest;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.services.delivery.AdminDeliveryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Администратор следит за доставками и решает проблемы. Все действия требуют причину и сохраняют её. */
@RestController
@RequestMapping("/api/v1/admin/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Delivery admin", description = "Контроль доставок и разбор проблем")
@SecurityRequirement(name = "bearerAuth")
public class AdminDeliveryController {
    private final AdminDeliveryService adminService;

    @Operation(summary = "Последние доставки", description = "Без status — все статусы")
    @GetMapping
    public ResponseEntity<List<DeliveryResponses.Admin>> deliveries(
            @RequestParam(required = false) DeliveryStatus status) {
        return ResponseEntity.ok(adminService.getDeliveries(status));
    }

    @Operation(summary = "Проблемы по доставкам", description = "Без status — все")
    @GetMapping("/issues")
    public ResponseEntity<List<DeliveryResponses.Issue>> issues(
            @RequestParam(required = false) DeliveryIssueStatus status) {
        return ResponseEntity.ok(adminService.getIssues(status));
    }

    @Operation(summary = "Снять курьера", description = "Только до того, как курьер забрал заказ; заказ вернётся в ленту")
    @PostMapping("/{deliveryId}/reassign")
    public ResponseEntity<DeliveryResponses.Admin> reassign(@PathVariable Long deliveryId,
                                                            @Valid @RequestBody AdminDeliveryActionRequest request) {
        return ResponseEntity.ok(adminService.reassign(deliveryId, request.reason()));
    }

    @Operation(summary = "Отменить доставку", description = "Заказ остаётся, заведение сможет выдать его само")
    @PostMapping("/{deliveryId}/cancel")
    public ResponseEntity<DeliveryResponses.Admin> cancel(@PathVariable Long deliveryId,
                                                          @Valid @RequestBody AdminDeliveryActionRequest request) {
        return ResponseEntity.ok(adminService.cancel(deliveryId, request.reason()));
    }

    @Operation(summary = "Завершить доставку без кода клиента",
            description = "Например, клиент подтвердил получение по телефону. Курьеру засчитывается оплата")
    @PostMapping("/{deliveryId}/complete")
    public ResponseEntity<DeliveryResponses.Admin> complete(@PathVariable Long deliveryId,
                                                            @Valid @RequestBody AdminDeliveryActionRequest request) {
        return ResponseEntity.ok(adminService.complete(deliveryId, request.reason()));
    }

    @Operation(summary = "Разблокировать ввод кодов", description = "Заблокированные коды заменяются новыми")
    @PostMapping("/{deliveryId}/unlock")
    public ResponseEntity<DeliveryResponses.Admin> unlock(@PathVariable Long deliveryId,
                                                          @Valid @RequestBody AdminDeliveryActionRequest request) {
        return ResponseEntity.ok(adminService.unlockCodes(deliveryId, request.reason()));
    }

    @Operation(summary = "Закрыть проблему")
    @PostMapping("/issues/{issueId}/resolve")
    public ResponseEntity<DeliveryResponses.Issue> resolve(@PathVariable Long issueId,
                                                           @Valid @RequestBody ResolveIssueRequest request) {
        return ResponseEntity.ok(adminService.resolveIssue(issueId, request.resolution()));
    }
}
