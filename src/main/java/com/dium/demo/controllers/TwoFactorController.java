package com.dium.demo.controllers;

import com.dium.demo.dto.requests.TwoFactorBackupCodesRequest;
import com.dium.demo.dto.requests.TwoFactorDisableRequest;
import com.dium.demo.dto.requests.TwoFactorEnableRequest;
import com.dium.demo.dto.requests.TwoFactorSetupRequest;
import com.dium.demo.dto.requests.TwoFactorTempTokenRequest;
import com.dium.demo.dto.requests.TwoFactorVerifyRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.BackupCodesResponse;
import com.dium.demo.dto.responses.TwoFactorSetupResponse;
import com.dium.demo.dto.responses.TwoFactorStatusResponse;
import com.dium.demo.services.auth.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
@Tag(name = "Two-factor authentication", description = "Второй фактор при входе (WhatsApp-код или TOTP) и управление им")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    // ---------- завершение входа (по временному токену, без JWT) ----------

    @Operation(
            summary = "Завершить вход: проверить код второго фактора",
            description = "Принимает tempToken из ответа /login и код (WhatsApp, TOTP или резервный). " +
                    "Только после успешной проверки возвращает настоящий JWT.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Код верный, в ответе JWT"),
                    @ApiResponse(responseCode = "401", description = "Временный токен недействителен или истёк (5 минут)"),
                    @ApiResponse(responseCode = "422", description = "Неверный или просроченный код"),
                    @ApiResponse(responseCode = "429", description = "Слишком много попыток")
            })
    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(twoFactorService.verifyLogin(request));
    }

    @Operation(
            summary = "Отправить (повторно) WhatsApp-код для входа",
            description = "Работает по tempToken. Не чаще одного раза в 60 секунд.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Код отправлен"),
                    @ApiResponse(responseCode = "401", description = "Временный токен недействителен или истёк"),
                    @ApiResponse(responseCode = "429", description = "Код уже отправлен, подождите"),
                    @ApiResponse(responseCode = "502", description = "WhatsApp недоступен, код не отправлен")
            })
    @PostMapping("/verify/send-code")
    public ResponseEntity<Map<String, String>> sendLoginCode(@Valid @RequestBody TwoFactorTempTokenRequest request) {
        twoFactorService.sendLoginCode(request.tempToken());
        return ResponseEntity.ok(Map.of("message", "Код отправлен в WhatsApp"));
    }

    // ---------- управление (нужен обычный JWT) ----------

    @Operation(summary = "Состояние 2FA текущего пользователя", security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/status")
    public ResponseEntity<TwoFactorStatusResponse> status() {
        return ResponseEntity.ok(twoFactorService.getStatus());
    }

    @Operation(
            summary = "Начать подключение 2FA",
            description = "TOTP: возвращает секрет, otpauth URI и QR-код для приложения-аутентификатора. " +
                    "WHATSAPP: отправляет проверочный код. Способ начинает действовать только после /2fa/enable.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup(@Valid @RequestBody TwoFactorSetupRequest request) {
        return ResponseEntity.ok(twoFactorService.setup(request.method()));
    }

    @Operation(
            summary = "Включить 2FA, подтвердив кодом",
            description = "Возвращает 10 одноразовых резервных кодов. Они показываются только один раз — на сервере хранятся хэши.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/enable")
    public ResponseEntity<BackupCodesResponse> enable(@Valid @RequestBody TwoFactorEnableRequest request) {
        return ResponseEntity.ok(twoFactorService.enable(request));
    }

    @Operation(
            summary = "Отправить WhatsApp-код текущему пользователю",
            description = "Нужен, чтобы отключить 2FA, если способ — WhatsApp.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/send-code")
    public ResponseEntity<Map<String, String>> sendCode() {
        twoFactorService.sendCodeToCurrentUser();
        return ResponseEntity.ok(Map.of("message", "Код отправлен в WhatsApp"));
    }

    @Operation(
            summary = "Отключить 2FA",
            description = "Требуются пароль и действующий код (или резервный код). " +
                    "Для ролей с обязательной 2FA вход после отключения всё равно подтверждается кодом в WhatsApp.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disable(@Valid @RequestBody TwoFactorDisableRequest request) {
        twoFactorService.disable(request);
        return ResponseEntity.ok(Map.of("message", "Двухфакторная аутентификация отключена"));
    }

    @Operation(
            summary = "Перевыпустить резервные коды",
            description = "Требуется пароль. Старые коды перестают действовать, новые возвращаются один раз.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/backup-codes")
    public ResponseEntity<BackupCodesResponse> backupCodes(@Valid @RequestBody TwoFactorBackupCodesRequest request) {
        return ResponseEntity.ok(twoFactorService.regenerateBackupCodes(request.password()));
    }
}
