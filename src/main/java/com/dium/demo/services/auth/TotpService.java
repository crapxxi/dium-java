package com.dium.demo.services.auth;

import com.dium.demo.config.TwoFactorProperties;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * TOTP (RFC 6238) для приложений-аутентификаторов: SHA1, 6 цифр, шаг 30 секунд —
 * параметры, которые поддерживает Google Authenticator.
 */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;

    private final TwoFactorProperties properties;
    private final Clock clock;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);
    private final DefaultCodeVerifier codeVerifier;

    public TotpService(TwoFactorProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, () -> clock.instant().getEpochSecond());
        this.codeVerifier.setTimePeriod(PERIOD_SECONDS);
        // допускаем расхождение часов телефона и сервера на один шаг (±30 секунд)
        this.codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    /** Новый случайный секрет в Base32. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    public boolean isValidCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        return codeVerifier.isValidCode(secret, digits);
    }

    /** Код, действительный прямо сейчас (используется в тестах). */
    public String currentCode(String secret) {
        try {
            return codeGenerator.generate(secret, Math.floorDiv(clock.instant().getEpochSecond(), PERIOD_SECONDS));
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("Не удалось сгенерировать TOTP-код", e);
        }
    }

    /** otpauth://-ссылка, которую понимают приложения-аутентификаторы. */
    public String buildOtpAuthUri(String accountLabel, String secret) {
        return qrData(accountLabel, secret).getUri();
    }

    /** QR-код с otpauth-ссылкой в виде data URI (image/png), готовый для {@code <img src>}. */
    public String buildQrCodeDataUri(String accountLabel, String secret) {
        ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
        generator.setImageSize(320);
        try {
            byte[] image = generator.generate(qrData(accountLabel, secret));
            return Utils.getDataUriForImage(image, generator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Не удалось сгенерировать QR-код", e);
        }
    }

    private QrData qrData(String accountLabel, String secret) {
        return new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(properties.issuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(DIGITS)
                .period(PERIOD_SECONDS)
                .build();
    }
}
