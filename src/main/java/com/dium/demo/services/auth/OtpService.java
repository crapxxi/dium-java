package com.dium.demo.services.auth;

import com.dium.demo.enums.OtpPurpose;

public interface OtpService {
    /**
     * Генерирует код и отправляет его пользователю.
     *
     * @param phone нормализованный номер (7XXXXXXXXXX)
     * @throws com.dium.demo.exceptions.TooManyRequestsException если предыдущий код отправлен менее 60 секунд назад
     * @throws com.dium.demo.exceptions.MessageDeliveryException если код не удалось доставить
     */
    void sendCode(String phone, OtpPurpose purpose);

    /**
     * Проверяет код и при успехе «сжигает» его.
     *
     * @throws com.dium.demo.exceptions.BusinessLogicException если код неверный, истёк, не запрашивался
     *                                                         или исчерпаны попытки
     */
    void verifyCode(String phone, OtpPurpose purpose, String code);
}
