package com.dium.demo.controllers;

import com.dium.demo.dto.requests.WalletAdjustmentRequest;
import com.dium.demo.dto.responses.WalletResponses;
import com.dium.demo.services.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Администратор: кошельки пользователей и сверка с заведениями. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Wallet admin", description = "Кошельки баллов и сверка с заведениями")
@SecurityRequirement(name = "bearerAuth")
public class AdminWalletController {
    private final WalletService walletService;

    @Operation(summary = "Кошелёк пользователя по телефону")
    @GetMapping("/wallets")
    public ResponseEntity<WalletResponses.AdminWallet> find(@RequestParam String phone) {
        return ResponseEntity.ok(walletService.findByPhone(phone));
    }

    @Operation(summary = "Начислить или списать баллы вручную", description = "Причина обязательна и сохраняется в журнале")
    @PostMapping("/wallets/{userId}/adjustments")
    public ResponseEntity<WalletResponses.AdminWallet> adjust(@PathVariable Long userId,
                                                              @Valid @RequestBody WalletAdjustmentRequest request) {
        return ResponseEntity.ok(walletService.adjust(userId, request.amount(), request.reason()));
    }

    @Operation(summary = "Сверка с заведениями",
            description = "Даты включительно; без дат — текущий месяц. Плюс в итоге — платформа платит заведению")
    @GetMapping("/settlements")
    public ResponseEntity<WalletResponses.SettlementReport> settlements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        return ResponseEntity.ok(walletService.getSettlements(start, end));
    }
}
