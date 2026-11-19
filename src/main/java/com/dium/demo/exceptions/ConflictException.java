package com.dium.demo.exceptions;

/** Действие опоздало: данные уже изменил кто-то другой (например, заказ взял другой курьер). HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
