package com.dium.demo.services.order.impl;

import com.dium.demo.models.Order;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.services.order.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KaspiPaymentServiceImpl implements PaymentService {
    private final OrderRepository orderRepository;

    @Override
    public void pay() {
        // PAYMENT logic cannot implement
    }

    @Transactional(readOnly = true)
    public String getKaspiUrl(Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId);

        Venue venue = order.getVenue();

        if (venue.getKaspiUrl() == null || venue.getKaspiUrl().isBlank()) {
            throw new EntityNotFoundException("Заведение еще не добавило ссылку Kaspi");
        }

        return venue.getKaspiUrl();
    }
}
