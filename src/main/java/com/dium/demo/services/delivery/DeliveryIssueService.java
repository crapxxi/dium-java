package com.dium.demo.services.delivery;

import com.dium.demo.dto.requests.DeliveryIssueRequest;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.models.Delivery;

/** Проблемы по доставке: их открывают участники или система, решает администратор. */
public interface DeliveryIssueService {
    /** Участник доставки (клиент, курьер или заведение) сообщает о проблеме. */
    DeliveryResponses.Issue report(Long deliveryId, DeliveryIssueRequest request);

    /** Проблема, которую создаёт система (например, блокировка кода). Дубликат открытой проблемы не создаётся. */
    void openSystemIssue(Delivery delivery, DeliveryIssueType type, String description);
}
