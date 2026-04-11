package com.dium.demo.services;

import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.OrderMapper;
import com.dium.demo.models.*;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final VenueRepository venueRepository;
    private final OrderMapper orderMapper;
    private final ModifierRepository modifierRepository;
    private final CustomUserDetailsService userDetailsService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        User user = userDetailsService.getCurrentUser();
        Venue venue = venueRepository.findByIdOrThrow(request.venueId());

        if (!venue.getIsWorking()) {
            throw new BusinessLogicException("Venue is not working");
        }

        Order order = new Order();
        order.setUser(user);
        order.setVenue(venue);
        order.setStatus(OrderStatus.PENDING);
        order.setComment(request.comment());

        BigDecimal totalSum = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        boolean isDelivery = venue.getCanDeliver() && request.address() != null && !request.address().isBlank();
        order.setAddress(isDelivery ? request.address() : null);

        for (OrderItemRequest itemRequest : request.items()) {
            if (itemRequest.count() == null || itemRequest.count() <= 0) {
                throw new BusinessLogicException("Count must be greater than 0");
            }

            Product product = productRepository.findByIdOrThrow(itemRequest.productId());

            if (!product.getInStock()) {
                throw new BusinessLogicException("Product " + product.getName() + " is not in stock");
            }

            if (!product.getVenue().getId().equals(venue.getId())) {
                throw new BusinessLogicException("Access denied: product is not from this venue");
            }

            BigDecimal itemPrice = product.getPrice();
            List<Modifier> selectedModifiers = new ArrayList<>();

            if (itemRequest.modifierIds() != null && !itemRequest.modifierIds().isEmpty()) {
                selectedModifiers = modifierRepository.findAllById(itemRequest.modifierIds());
                for (Modifier modifier : selectedModifiers) {
                    if (!modifier.getModifierGroup().getProduct().getId().equals(product.getId())) {
                        throw new BusinessLogicException("Access denied: invalid modifier for product: " + product.getName());
                    }
                    itemPrice = itemPrice.add(modifier.getPriceDelta());
                }
            }

            if (itemPrice.compareTo(BigDecimal.ZERO) < 0) {
                itemPrice = BigDecimal.ZERO;
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setCount(itemRequest.count());
            orderItem.setPriceAtPurchase(itemPrice);
            orderItem.setModifiers(selectedModifiers);

            totalSum = totalSum.add(itemPrice.multiply(BigDecimal.valueOf(itemRequest.count())));
            orderItems.add(orderItem);
        }

        if (isDelivery && venue.getDeliveryPrice() != null) {
            BigDecimal deliveryFee = venue.getDeliveryPrice();
            order.setDeliveryFee(deliveryFee);
            totalSum = totalSum.add(deliveryFee);
        } else {
            order.setDeliveryFee(BigDecimal.ZERO);
        }

        if (totalSum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessLogicException("Order total sum must be greater than zero");
        }

        order.setItems(orderItems);
        order.setTotalSum(totalSum);

        String finalPaymentFrom = (request.paymentFrom() != null && !request.paymentFrom().isEmpty())
                ? request.paymentFrom()
                : user.getPaymentFrom();

        if (finalPaymentFrom == null || finalPaymentFrom.isEmpty()) {
            throw new BusinessLogicException("Payment from is required!");
        }

        order.setPaymentFrom(finalPaymentFrom);

        if (user.getPaymentFrom() == null || user.getPaymentFrom().isEmpty()) {
            user.setPaymentFrom(finalPaymentFrom);
        }

        Order savedOrder = orderRepository.save(order);
        savedOrder.setPickupCode(generatePickupCode(savedOrder.getId()));

        return orderMapper.toResponse(savedOrder);
    }
    @Transactional
    public void updateStatus(Long orderId) {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        Order order = orderRepository.findByIdOrThrow(orderId);

        checkAccess(user, order);

        switch (order.getStatus()) {
            case PENDING -> order.setStatus(OrderStatus.PREPARING);
            case PREPARING -> order.setStatus(OrderStatus.READY);
            case READY -> order.setStatus(OrderStatus.COMPLETED);
            default -> throw new BusinessLogicException("Wrong status type");
        }
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        User user = userDetailsService.getCurrentUser();

        checkVenueOwner(user);

        Order order = orderRepository.findByIdOrThrow(orderId);

        checkAccess(user, order);

        if(!order.getStatus().equals(OrderStatus.PENDING))
            throw new BusinessLogicException("Order is accepted, can't cancel");

        order.setStatus(OrderStatus.CANCELLED);
    }

    private Integer generatePickupCode(Long orderId) {
        return (int) ((orderId * 48271) % 9000 + 1000);
    }
    public List<OrderResponse> getOrdersByUserId() {
        User user = userDetailsService.getCurrentUser();

        return orderMapper.toResponseList(orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public String getKaspiUrl(Long orderId) {
        Order order = orderRepository.findByIdOrThrow(orderId);

        Venue venue = order.getVenue();

        if (venue.getKaspiUrl() == null || venue.getKaspiUrl().isBlank()) {
            throw new EntityNotFoundException("Заведение еще не добавило ссылку Kaspi");
        }

        return venue.getKaspiUrl();
    }

    private void checkVenueOwner(User user) {
        if(!user.getRole().equals(UserRole.VENUE_OWNER))
            throw new AccessDeniedException("Access denied: not venue owner");
    }

    private void checkAccess(User user, Order order) {
        if(!order.getVenue().getOwner().getId().equals(user.getId()))
            throw new AccessDeniedException("Access denied: venue doesn't own this order");
    }

}
