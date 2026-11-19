package com.dium.demo.services.delivery;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.exceptions.InvalidHandoverCodeException;
import com.dium.demo.models.Delivery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Проверка кодов передачи с ограничением попыток.
 * <p>
 * Каждый неверный ввод увеличивает счётчик в доставке. Когда попытки кончаются, код блокируется
 * (дальше не принимается даже верный) и создаётся проблема для администратора: подобрать код
 * перебором нельзя, а спорная передача не проходит молча.
 * <p>
 * Вызывающий метод должен быть помечен {@code @Transactional(noRollbackFor = InvalidHandoverCodeException.class)},
 * иначе счётчик откатится вместе с ошибкой.
 */
@Component
@RequiredArgsConstructor
public class HandoverCodeVerifier {
    private final DeliveryCodes codes;
    private final DeliveryProperties properties;
    private final DeliveryIssueService issueService;

    /** @throws InvalidHandoverCodeException если код неверный или ввод заблокирован */
    public void verify(Delivery delivery, HandoverCode code, String input) {
        int max = properties.maxCodeAttempts();
        if (code.isLocked(delivery, max)) {
            throw new InvalidHandoverCodeException(
                    "Ввод кода заблокирован после неверных попыток. Администратор уже видит проблему и разберётся");
        }
        if (codes.matches(code.sealedCode(delivery), input)) {
            return;
        }

        int attempts = code.attempts(delivery) + 1;
        code.setAttempts(delivery, attempts);
        int left = max - attempts;
        if (left > 0) {
            throw new InvalidHandoverCodeException("Неверный код. Осталось попыток: " + left);
        }

        issueService.openSystemIssue(delivery, DeliveryIssueType.CODE_LOCKED,
                "Неверно введён " + code.label() + " " + attempts + " раз подряд, ввод заблокирован");
        throw new InvalidHandoverCodeException(
                "Неверный код. Попытки закончились — ввод заблокирован, администратор разберётся");
    }
}
