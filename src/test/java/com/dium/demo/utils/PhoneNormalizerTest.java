package com.dium.demo.utils;

import com.dium.demo.exceptions.BusinessLogicException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "+7 (707) 123-45-67",
            "8 707 123 45 67",
            "8(707)1234567",
            "77071234567",
            "+77071234567",
            "7071234567",
            " 8-707-123-45-67 ",
            "+7 707 123.45.67"
    })
    void normalizesAllCommonKzFormatsToSingleForm(String raw) {
        assertThat(PhoneNormalizer.normalize(raw)).isEqualTo("77071234567");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "12345",                 // слишком короткий
            "770712345678",          // 12 цифр
            "97071234567",           // 11 цифр, но не с 7/8
            "+1 707 123 45 67",      // не казахстанский код страны
            "8707123456a",           // буквы
            "7707<script>1234567"    // посторонние символы
    })
    void rejectsInvalidNumbers(String raw) {
        assertThatThrownBy(() -> PhoneNormalizer.normalize(raw))
                .isInstanceOf(BusinessLogicException.class);
        assertThat(PhoneNormalizer.isValid(raw)).isFalse();
    }
}
