package com.dium.demo.services.delivery;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.security.SecretEncryptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Одноразовые коды передачи заказа.
 * <p>
 * Код случайный ({@link SecureRandom}), в БД лежит только в зашифрованном виде (AES-256-GCM через
 * {@link SecretEncryptor}), сравнивается за постоянное время.
 */
@Component
public class DeliveryCodes {
    private final SecretEncryptor encryptor;
    private final int length;
    private final int bound;
    private final SecureRandom random = new SecureRandom();

    public DeliveryCodes(SecretEncryptor encryptor, DeliveryProperties properties) {
        this.encryptor = encryptor;
        this.length = properties.codeLength();
        this.bound = (int) Math.pow(10, length);
    }

    /** Новый код сразу в зашифрованном виде — для сохранения в доставке. */
    public String newSealedCode() {
        return encryptor.encrypt(String.format("%0" + length + "d", random.nextInt(bound)));
    }

    /** Расшифровывает код, чтобы показать его владельцу (клиенту или курьеру). */
    public String reveal(String sealedCode) {
        return sealedCode == null ? null : encryptor.decrypt(sealedCode);
    }

    public boolean matches(String sealedCode, String input) {
        if (sealedCode == null || input == null) {
            return false;
        }
        return MessageDigest.isEqual(
                reveal(sealedCode).getBytes(StandardCharsets.UTF_8),
                input.trim().getBytes(StandardCharsets.UTF_8));
    }
}
