package com.dium.demo.controllers;

import com.dium.demo.dto.requests.ResetPasswordRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.services.auth.ActivationService;
import com.dium.demo.services.auth.AuthService;
import com.dium.demo.services.auth.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Регистрация, подтверждение телефона, вход и сброс пароля")
public class AuthController {

    private final AuthService authService;
    private final ActivationService activationService;
    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "Регистрация клиента",
            description = "Создаёт неподтверждённый аккаунт и отправляет код в WhatsApp. " +
                    "Если код отправить не удалось, аккаунт не создаётся и возвращается 502.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Аккаунт создан, код отправлен"),
                    @ApiResponse(responseCode = "403", description = "Номер уже зарегистрирован и подтверждён"),
                    @ApiResponse(responseCode = "422", description = "Некорректный номер телефона"),
                    @ApiResponse(responseCode = "429", description = "Код уже отправлен менее 60 секунд назад / лимит запросов"),
                    @ApiResponse(responseCode = "502", description = "Не удалось отправить код в WhatsApp")
            })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Регистрация владельца заведения (только ADMIN)", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/venue-register")
    public ResponseEntity<UserResponse> venueRegister(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.venueRegister(request));
    }

    @Operation(
            summary = "Вход по телефону и паролю",
            description = "Без 2FA возвращает JWT. Если у пользователя включена (или обязательна для роли) 2FA — " +
                    "JWT НЕ выдаётся: в ответе twoFactorRequired=true и tempToken (5 минут), " +
                    "вход завершается через POST /api/v1/auth/2fa/verify.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "JWT либо требование второго фактора"),
                    @ApiResponse(responseCode = "401", description = "Неверный телефон или пароль"),
                    @ApiResponse(responseCode = "422", description = "ACCOUNT_LOCKED: телефон не подтверждён, код активации отправлен"),
                    @ApiResponse(responseCode = "429", description = "Лимит запросов")
            })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Профиль текущего пользователя", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(authService.getMe());
    }

    @Operation(
            summary = "Подтверждение телефона кодом из WhatsApp",
            description = "Код действует 5 минут и аннулируется после 5 неверных попыток.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Телефон подтверждён, в ответе JWT"),
                    @ApiResponse(responseCode = "422", description = "Неверный/просроченный код или исчерпаны попытки")
            })
    @PostMapping("/activate")
    public ResponseEntity<AuthResponse> activate(@RequestParam String phone, @RequestParam String code) {
        return ResponseEntity.ok(activationService.activateAccount(phone, code));
    }

    @Operation(
            summary = "Повторная отправка кода активации",
            description = "Не чаще одного раза в 60 секунд на номер.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Код отправлен"),
                    @ApiResponse(responseCode = "429", description = "Код уже отправлен, подождите (заголовок Retry-After)"),
                    @ApiResponse(responseCode = "502", description = "Не удалось отправить код в WhatsApp")
            })
    @PostMapping("/resend-code")
    public ResponseEntity<Void> resendCode(@RequestParam String phone) {
        activationService.resendActivationCode(phone);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Запросить код для сброса пароля",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Код отправлен в WhatsApp"),
                    @ApiResponse(responseCode = "422", description = "Пользователь не найден / некорректный номер"),
                    @ApiResponse(responseCode = "429", description = "Код уже отправлен, подождите"),
                    @ApiResponse(responseCode = "502", description = "Не удалось отправить код в WhatsApp")
            })
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestParam String phone) {
        passwordResetService.requestPasswordReset(phone);
        return ResponseEntity.ok(Map.of("message", "Код отправлен в WhatsApp"));
    }

    @Operation(summary = "Установить новый пароль по коду из WhatsApp")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Пароль успешно изменен"));
    }
}
