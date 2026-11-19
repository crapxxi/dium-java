package com.dium.demo.services.delivery;

import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryStatus;

import java.util.List;

/** Администратор: следит за доставками и решает проблемы. Каждое действие требует причину. */
public interface AdminDeliveryService {
    /** Последние доставки; {@code status == null} — все. */
    List<DeliveryResponses.Admin> getDeliveries(DeliveryStatus status);

    /** Проблемы; {@code status == null} — все. */
    List<DeliveryResponses.Issue> getIssues(DeliveryIssueStatus status);

    /** Снять курьера (до получения заказа): заказ возвращается в ленту. */
    DeliveryResponses.Admin reassign(Long deliveryId, String reason);

    DeliveryResponses.Admin cancel(Long deliveryId, String reason);

    /** Завершить доставку без кода клиента (например, клиент подтвердил получение по телефону). */
    DeliveryResponses.Admin complete(Long deliveryId, String reason);

    /** Сбросить счётчики неверных вводов; заблокированные коды заменяются новыми. */
    DeliveryResponses.Admin unlockCodes(Long deliveryId, String reason);

    DeliveryResponses.Issue resolveIssue(Long issueId, String resolution);
}
