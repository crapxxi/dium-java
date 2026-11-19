package com.dium.demo.exceptions;

/**
 * Неверный или заблокированный код передачи заказа. HTTP 422.
 * <p>
 * Отдельный тип нужен для {@code @Transactional(noRollbackFor = ...)}: счётчик неверных попыток
 * должен сохраниться, хотя запрос завершается ошибкой.
 */
public class InvalidHandoverCodeException extends BusinessLogicException {
    public InvalidHandoverCodeException(String message) {
        super(message);
    }
}
