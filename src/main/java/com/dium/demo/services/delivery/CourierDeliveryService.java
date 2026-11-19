package com.dium.demo.services.delivery;

import com.dium.demo.dto.responses.DeliveryResponses;

import java.util.List;

/** Студент в роли курьера: берёт свободные заказы, забирает их в заведении и отдаёт клиенту по коду. */
public interface CourierDeliveryService {
    /** Заказы, которые ждут курьера (кроме собственных заказов студента). */
    List<DeliveryResponses.Available> getAvailable();

    /** Взять заказ. Если его успел взять другой курьер — 409. */
    DeliveryResponses.Courier accept(Long deliveryId);

    /** Отказаться от заказа, пока он не получен в заведении. */
    DeliveryResponses.Courier release(Long deliveryId);

    /** Отдать заказ клиенту: курьер вводит код, который назвал клиент. */
    DeliveryResponses.Courier confirmDelivered(Long deliveryId, String customerCode);

    List<DeliveryResponses.Courier> getMyDeliveries();

    DeliveryResponses.CourierSummary getSummary();
}
