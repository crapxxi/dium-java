package com.dium.demo.services.delivery;

import com.dium.demo.dto.responses.DeliveryResponses;

import java.util.List;

/** Студент в роли заказчика: видит, кто везёт его заказ, и свой код для получения. */
public interface CustomerDeliveryService {
    List<DeliveryResponses.Customer> getMyOrderDeliveries();
}
