package com.dium.demo.security;

import com.dium.demo.config.TwoFactorProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шифрует TOTP-секреты перед сохранением в БД: AES-256-GCM, случайный 96-битный IV на каждое значение.
 * Формат: {@code v1:Base64(IV || ciphertext || tag)}.
 * <p>
 * Ключ задаётся переменной {@code TWO_FACTOR_ENCRYPTION_KEY} (Base64 от 32 байт).
 */
@Component
public class SecretEncryptor {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptor(TwoFactorProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.encryptionKey()), "AES");
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "Не задан ключ шифрования 2FA: установите TWO_FACTOR_ENCRYPTION_KEY (openssl rand -base64 32)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TWO_FACTOR_ENCRYPTION_KEY должен быть строкой Base64", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "TWO_FACTOR_ENCRYPTION_KEY должен содержать ровно 32 байта (сейчас " + bytes.length + ")");
        }
        return bytes;
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось зашифровать секрет", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || !cipherText.startsWith(PREFIX)) {
            throw new IllegalStateException("Неизвестный формат зашифрованного секрета");
        }
        try {
            byte[] data = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Неверный ключ или повреждённые данные
            throw new IllegalStateException("Не удалось расшифровать секрет (изменился TWO_FACTOR_ENCRYPTION_KEY?)", e);
        }
    }
}
