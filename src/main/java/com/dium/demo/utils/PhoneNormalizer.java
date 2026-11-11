package com.dium.demo.utils;

import com.dium.demo.exceptions.BusinessLogicException;

import java.util.regex.Pattern;

/**
 * Приводит казахстанские номера к единому виду {@code 7XXXXXXXXXX} (11 цифр, без плюса).
 * <p>
 * Принимает: {@code +7 (707) 123-45-67}, {@code 8 707 123 45 67}, {@code 77071234567}, {@code 7071234567}.
 * В этом же виде номер хранится в БД, используется как ключ OTP и уходит в Green API ({@code 7XXXXXXXXXX@c.us}).
 */
public final class PhoneNormalizer {

    private static final Pattern NORMALIZED = Pattern.compile("^7\\d{10}$");
    private static final Pattern ALLOWED_INPUT = Pattern.compile("^[\\d\\s()+\\-.]+$");

    public static final String INVALID_PHONE_MESSAGE =
            "Некорректный номер телефона. Введите номер в формате +7 7XX XXX XX XX";

    private PhoneNormalizer() {
    }

    /**
     * @throws BusinessLogicException если номер нельзя привести к формату 7XXXXXXXXXX
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank() || !ALLOWED_INPUT.matcher(rawPhone.trim()).matches()) {
            throw new BusinessLogicException(INVALID_PHONE_MESSAGE);
        }

        String digits = rawPhone.replaceAll("\\D", "");

        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        } else if (digits.length() == 10) {
            digits = "7" + digits;
        }

        if (!NORMALIZED.matcher(digits).matches()) {
            throw new BusinessLogicException(INVALID_PHONE_MESSAGE);
        }
        return digits;
    }

    public static boolean isValid(String rawPhone) {
        try {
            normalize(rawPhone);
            return true;
        } catch (BusinessLogicException e) {
            return false;
        }
    }
}
