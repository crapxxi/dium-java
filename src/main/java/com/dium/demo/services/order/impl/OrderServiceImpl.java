package com.dium.demo.services.order.impl;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.FulfillmentType;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.PaymentMethod;
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
import com.dium.demo.services.order.OrderStatusListener;
import com.dium.demo.services.wallet.PointsPricing;
import com.dium.demo.services.wallet.WalletOperations;
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
    /** Другие модули (например, доставка), которым важна смена статуса заказа. */
    private final List<OrderStatusListener> statusListeners;
    private final DeliveryProperties deliveryProperties;
    private final PointsPricing pointsPricing;
    private final WalletOperations walletOperations;

    @Transactional
    @Override
    public OrderResponse createOrder(OrderRequest request) {
        User user = userDetailsService.getCurrentUser();
        Venue venue = venueRepository.findByIdOrThrow(request.venueId());

        if (!Boolean.TRUE.equals(venue.getIsWorking())) {
            throw new BusinessLogicException("Venue is not working");
        }

        boolean delivery = isDelivery(venue, request);
        // Доставка стоит одинаково за весь заказ, сколько бы в нём ни было товаров
        BigDecimal deliveryFee = delivery ? deliveryProperties.fee() : BigDecimal.ZERO;

        PaymentMethod paymentMethod = request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.KASPI;
        boolean paidWithPoints = paymentMethod == PaymentMethod.POINTS;

        Order order = Order.builder()
                .user(user)
                .venue(venue)
                .status(OrderStatus.PENDING)
                .comment(request.comment())
                .address(delivery ? request.address() : null)
                .deliveryFee(deliveryFee)
                .paymentMethod(paymentMethod)
                .paymentFrom(paidWithPoints ? null : resolvePaymentFrom(request, user))
                .build();

        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            items.add(orderItemFactory.create(order, itemRequest));
        }
        order.setItems(items);
        order.setTotalSum(totalSum(items, deliveryFee));

        if (paidWithPoints) {
            // Цена в баллах считается на сервере по ценам из БД: клиент не может прислать свою
            long pointsDelivery = pointsPricing.priceInPoints(deliveryFee);
            order.setPointsDelivery(pointsDelivery);
            order.setPointsSpent(pointsPricing.priceInPoints(order.getTotalSum().subtract(deliveryFee)) + pointsDelivery);
        }

        Order savedOrder = orderRepository.save(order);
        savedOrder.setPickupCode(generatePickupCode(savedOrder.getId()));
        if (paidWithPoints) {
            // В той же транзакции: не хватит баллов — заказ не создастся
            walletOperations.payOrder(savedOrder);
        }

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

        changeStatus(order, next);
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

        changeStatus(order, OrderStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByUserId() {
        User user = userDetailsService.getCurrentUser();
        return orderMapper.toResponseList(orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    private void changeStatus(Order order, OrderStatus next) {
        OrderStatus previous = order.getStatus();
        statusListeners.forEach(listener -> listener.beforeStatusChange(order, next));
        order.setStatus(next);
        statusListeners.forEach(listener -> listener.afterStatusChange(order, previous));
        notificationService.notifyCustomerAboutStatus(order);
    }

    /**
     * Самовывоз или доставка. Решает покупатель: при самовывозе адрес игнорируется и заказ
     * не попадает в ленту курьеров; доставка требует адрес и заведение, которое доставляет.
     */
    private boolean isDelivery(Venue venue, OrderRequest request) {
        boolean venueDelivers = Boolean.TRUE.equals(venue.getCanDeliver());
        boolean hasAddress = request.address() != null && !request.address().isBlank();
        if (request.fulfillment() == null) {
            // Клиенты без поля fulfillment: как раньше, доставка — если есть адрес
            return venueDelivers && hasAddress;
        }
        if (request.fulfillment() == FulfillmentType.PICKUP) {
            return false;
        }
        if (!venueDelivers) {
            throw new BusinessLogicException("Это заведение не доставляет — выберите самовывоз");
        }
        if (!hasAddress) {
            throw new BusinessLogicException("Укажите адрес доставки");
        }
        return true;
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
