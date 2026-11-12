package com.dium.demo.services.order.impl;

import com.dium.demo.models.Venue;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.services.order.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Оплата переводом по Kaspi-ссылке заведения. */
@Service
@RequiredArgsConstructor
public class KaspiPaymentServiceImpl implements PaymentService {
    private final OrderRepository orderRepository;

    @Override
    public void pay() {
        // Kaspi оплачивается переводом по ссылке заведения: серверу проводить нечего
    }

    @Transactional(readOnly = true)
    @Override
    public String getPaymentUrl(Long orderId) {
        Venue venue = orderRepository.findByIdOrThrow(orderId).getVenue();

        if (venue.getKaspiUrl() == null || venue.getKaspiUrl().isBlank()) {
            throw new EntityNotFoundException("Заведение еще не добавило ссылку Kaspi");
        }

        return venue.getKaspiUrl();
    }
}
