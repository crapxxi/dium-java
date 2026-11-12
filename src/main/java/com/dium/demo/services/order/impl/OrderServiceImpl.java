package com.dium.demo.services.order.impl;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.OrderMapper;
import com.dium.demo.models.Order;
import com.dium.demo.models.OrderItem;
import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.security.access.CheckOwnership;
import com.dium.demo.security.access.OwnedResource;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.order.OrderItemFactory;
import com.dium.demo.services.order.OrderNotificationService;
import com.dium.demo.services.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final VenueRepository venueRepository;
    private final OrderMapper orderMapper;
    private final CustomUserDetailsService userDetailsService;
    private final OrderItemFactory orderItemFactory;
    private final OrderNotificationService notificationService;

    @Transactional
    @Override
    public OrderResponse createOrder(OrderRequest request) {
        User user = userDetailsService.getCurrentUser();
        Venue venue = venueRepository.findByIdOrThrow(request.venueId());

        if (!Boolean.TRUE.equals(venue.getIsWorking())) {
            throw new BusinessLogicException("Venue is not working");
        }

        boolean delivery = isDelivery(venue, request);
        BigDecimal deliveryFee = delivery && venue.getDeliveryPrice() != null
                ? venue.getDeliveryPrice()
                : BigDecimal.ZERO;

        Order order = Order.builder()
                .user(user)
                .venue(venue)
                .status(OrderStatus.PENDING)
                .comment(request.comment())
                .address(delivery ? request.address() : null)
                .deliveryFee(deliveryFee)
                .paymentFrom(resolvePaymentFrom(request, user))
                .build();

        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            items.add(orderItemFactory.create(order, itemRequest));
        }
        order.setItems(items);
        order.setTotalSum(totalSum(items, deliveryFee));

        Order savedOrder = orderRepository.save(order);
        savedOrder.setPickupCode(generatePickupCode(savedOrder.getId()));

        return orderMapper.toResponse(savedOrder);
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.ORDER)
    @Transactional
    @Override
    public void updateStatus(Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId);

        OrderStatus next = order.getStatus().next()
                .orElseThrow(() -> new BusinessLogicException("Wrong status type"));

        order.setStatus(next);
        notificationService.notifyCustomerAboutStatus(order);
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.ORDER)
    @Transactional
    @Override
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessLogicException("Order is accepted, can't cancel");
        }

        order.setStatus(OrderStatus.CANCELLED);
        notificationService.notifyCustomerAboutStatus(order);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByUserId() {
        User user = userDetailsService.getCurrentUser();
        return orderMapper.toResponseList(orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    private boolean isDelivery(Venue venue, OrderRequest request) {
        return Boolean.TRUE.equals(venue.getCanDeliver())
                && request.address() != null
                && !request.address().isBlank();
    }

    /** Плательщик из заказа, а если не указан — сохранённый у пользователя. Первый указанный запоминается. */
    private String resolvePaymentFrom(OrderRequest request, User user) {
        String paymentFrom = hasText(request.paymentFrom()) ? request.paymentFrom() : user.getPaymentFrom();

        if (!hasText(paymentFrom)) {
            throw new BusinessLogicException("Payment from is required!");
        }
        if (!hasText(user.getPaymentFrom())) {
            user.setPaymentFrom(paymentFrom);
        }
        return paymentFrom;
    }

    private BigDecimal totalSum(List<OrderItem> items, BigDecimal deliveryFee) {
        BigDecimal total = items.stream()
                .map(item -> item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getCount())))
                .reduce(deliveryFee, BigDecimal::add);

        if (total.signum() <= 0) {
            throw new BusinessLogicException("Order total sum must be greater than zero");
        }
        return total;
    }

    private Integer generatePickupCode(Long orderId) {
        return (int) ((orderId * 48271) % 9000 + 1000);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
