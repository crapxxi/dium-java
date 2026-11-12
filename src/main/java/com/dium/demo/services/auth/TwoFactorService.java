package com.dium.demo.services.auth;

import com.dium.demo.dto.requests.TwoFactorDisableRequest;
import com.dium.demo.dto.requests.TwoFactorEnableRequest;
import com.dium.demo.dto.requests.TwoFactorVerifyRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.BackupCodesResponse;
import com.dium.demo.dto.responses.TwoFactorSetupResponse;
import com.dium.demo.dto.responses.TwoFactorStatusResponse;
import com.dium.demo.enums.TwoFactorMethod;
import com.dium.demo.models.User;

public interface TwoFactorService {

    // ---------- вход ----------

    /** Нужно ли запрашивать второй фактор при входе (включён пользователем или обязателен для роли). */
    boolean isLoginChallengeRequired(User user);

    /** Пароль уже проверен: выдаёт временный токен и (для WhatsApp) отправляет код. JWT не выдаётся. */
    AuthResponse startLoginChallenge(User user);

    /** Повторная отправка WhatsApp-кода по временному токену. */
    void sendLoginCode(String tempToken);

    /** Проверяет код и только после этого выдаёт настоящий JWT. */
    AuthResponse verifyLogin(TwoFactorVerifyRequest request);

    // ---------- управление (для авторизованного пользователя) ----------

    TwoFactorStatusResponse getStatus();

    /** Начало подключения: TOTP — новый секрет + otpauth URI; WhatsApp — отправка проверочного кода. */
    TwoFactorSetupResponse setup(TwoFactorMethod method);

    /** Подтверждение кодом и включение 2FA. Возвращает резервные коды (показываются один раз). */
    BackupCodesResponse enable(TwoFactorEnableRequest request);

    /** Отправляет WhatsApp-код текущему пользователю (нужен, например, для отключения 2FA). */
    void sendCodeToCurrentUser();

    /** Отключение: требуется пароль и действующий код. */
    void disable(TwoFactorDisableRequest request);

    /** Перевыпуск резервных кодов (старые перестают действовать). Требуется пароль. */
    BackupCodesResponse regenerateBackupCodes(String password);
}
