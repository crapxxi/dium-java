package com.dium.demo.controllers;

import com.dium.demo.dto.responses.WalletResponses;
import com.dium.demo.services.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Баллы: зарабатываются доставкой, тратятся на заказы")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {
    private final WalletService walletService;

    @Operation(summary = "Мой кошелёк", description = "Баланс, наценка при оплате баллами и последние 100 операций")
    @GetMapping
    public ResponseEntity<WalletResponses.Wallet> myWallet() {
        return ResponseEntity.ok(walletService.getMyWallet());
    }
}
