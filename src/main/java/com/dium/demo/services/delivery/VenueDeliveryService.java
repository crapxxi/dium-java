package com.dium.demo.services.delivery;

import com.dium.demo.dto.responses.DeliveryResponses;

import java.util.List;

/** Владелец заведения: видит, кто везёт его заказы, и отдаёт заказ курьеру по коду курьера. */
public interface VenueDeliveryService {
    List<DeliveryResponses.Venue> getVenueDeliveries();

    /** Отдать заказ курьеру: заведение вводит код, который показал курьер. */
    DeliveryResponses.Venue confirmPickup(Long deliveryId, String courierCode);
}
