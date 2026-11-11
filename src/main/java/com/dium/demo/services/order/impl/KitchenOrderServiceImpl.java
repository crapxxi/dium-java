package com.dium.demo.services.order.impl;

import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.mappers.OrderMapper;
import com.dium.demo.models.User;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.order.KitchenOrderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KitchenOrderServiceImpl implements KitchenOrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CustomUserDetailsService userDetailsService;
    private final VenueRepository venueRepository;

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getKitchenOrders() {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        Long venueId = venueRepository.findByOwner_Id(user.getId())
                .orElseThrow(()-> new EntityNotFoundException("User doesn't have a venue")).getId();

        List<OrderStatus> hiddenStatuses = List.of(
                OrderStatus.COMPLETED,
                OrderStatus.CANCELLED
        );


        return orderMapper.toResponseList(orderRepository.findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(
                venueId,
                hiddenStatuses));
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrderHistory() {
        User user = userDetailsService.getCurrentUser();
        checkVenueOwner(user);

        Long venueId = venueRepository.findByOwner_Id(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("User doesn't have a venue")).getId();

        List<OrderStatus> hiddenStatuses = List.of(
                OrderStatus.PENDING,
                OrderStatus.READY,
                OrderStatus.PREPARING
        );

        return orderMapper.toResponseList(orderRepository.findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(
                venueId,
                hiddenStatuses
        ));
    }

    private void checkVenueOwner(User user) {
        if(!user.getRole().equals(UserRole.VENUE_OWNER))
            throw new AccessDeniedException("Access denied: not venue owner");
    }
}
