package com.dium.demo.services.delivery.impl;

import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.delivery.CustomerDeliveryService;
import com.dium.demo.services.delivery.DeliveryViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerDeliveryServiceImpl implements CustomerDeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final CustomUserDetailsService userDetailsService;
    private final DeliveryViews views;

    @Transactional(readOnly = true)
    @Override
    public List<DeliveryResponses.Customer> getMyOrderDeliveries() {
        Long customerId = userDetailsService.getCurrentUser().getId();
        return deliveryRepository.findRecentForCustomer(customerId).stream().map(views::forCustomer).toList();
    }
}
