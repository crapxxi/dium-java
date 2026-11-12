package com.dium.demo.services.order.impl;

import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.OrderMapper;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.order.KitchenOrderService;
import com.dium.demo.services.venue.CurrentVenueProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KitchenOrderServiceImpl implements KitchenOrderService {
    /** Завершённые заказы: показываются только в истории. */
    private static final List<OrderStatus> FINISHED = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    /** Заказы в работе: показываются только на кухне. */
    private static final List<OrderStatus> ACTIVE =
            List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CurrentVenueProvider currentVenueProvider;

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getKitchenOrders() {
        return currentVenueOrdersExcluding(FINISHED);
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrderHistory() {
        return currentVenueOrdersExcluding(ACTIVE);
    }

    private List<OrderResponse> currentVenueOrdersExcluding(List<OrderStatus> hiddenStatuses) {
        Long venueId = currentVenueProvider.getCurrentVenue().getId();

        return orderMapper.toResponseList(
                orderRepository.findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(venueId, hiddenStatuses));
    }
}
