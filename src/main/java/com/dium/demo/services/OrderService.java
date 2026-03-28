package com.dium.demo.services;

import com.dium.demo.dto.order.CreateOrderRequest;
import com.dium.demo.dto.order.OrderItemRequest;
import com.dium.demo.dto.order.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.mappers.OrderMapper;
import com.dium.demo.models.*;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Transactional
    public OrderResponse createOrder(UserDetails userDetails, CreateOrderRequest request) {
        User user = (User) userDetails;

        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new RuntimeException("Venue not found"));

        Order order = new Order();
        order.setUser(user);
        order.setVenue(venue);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalSum = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        boolean isDelivery = venue.getCanDeliver() && request.address() != null && !request.address().isBlank();
        order.setAddress(venue.getCanDeliver() ? request.address() : null);

        for(OrderItemRequest itemRequest : request.items()) {
            if (itemRequest.count() <= 0) {
                throw new RuntimeException("Invalid count: count must be greater than 0");
            }
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemRequest.productId()));

            if(!product.getInStock())
                throw new RuntimeException("Product is not in stock");

            if (!product.getVenue().getId().equals(venue.getId())) {
                throw new RuntimeException("Product " + product.getName() + " is not from this venue");
            }

            BigDecimal itemPrice = product.getPrice();

            List<Modifier> selectedModifiers = modifierRepository.findAllById(itemRequest.modifierIds());
            for(Modifier modifier : selectedModifiers) {
                if(!modifier.getModifierGroup().getProduct().getId().equals(product.getId()))
                    throw new RuntimeException("invalid modifier");
                itemPrice = itemPrice.add(modifier.getPriceDelta());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setCount(itemRequest.count());
            orderItem.setPriceAtPurchase(itemPrice);
            orderItem.setModifiers(selectedModifiers);

            BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(itemRequest.count()));
            totalSum = totalSum.add(itemTotal);
            orderItems.add(orderItem);
        }
        if(isDelivery && venue.getDeliveryPrice() != null) {
            order.setDeliveryFee(venue.getDeliveryPrice());
            totalSum = totalSum.add(venue.getDeliveryPrice());
        } else {
            order.setDeliveryFee(BigDecimal.ZERO);
        }

        order.setItems(orderItems);
        order.setTotalSum(totalSum);
        order.setComment(request.comment());

        Order savedOrder = orderRepository.save(order);

        savedOrder.setPickupCode(generatePickupCode(savedOrder.getId()));

        return orderMapper.toResponse(orderRepository.save(savedOrder));
    }

    @Transactional
    public OrderResponse updateStatus(UserDetails userDetails, Long orderId, OrderStatus newStatus) {
        if(!(userDetails instanceof  User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("You haven't permission");

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
//        if(newStatus == OrderStatus.READY) {
//            // Уведомление
//        }
        order.setStatus(newStatus);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    private Integer generatePickupCode(Long orderId) {
        return (int) ((orderId * 48271) % 9000 + 1000);
    }
    public List<OrderResponse> getOrdersByUserId(UserDetails userDetails) {

        if(!(userDetails instanceof User user))
            throw new RuntimeException("not authorized");

        return orderMapper.toResponseList(orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getKitchenOrders(UserDetails userDetails) {
        if(!(userDetails instanceof User user) || user.getRole() != UserRole.VENUE_OWNER)
            throw new RuntimeException("Only a venue owner can get kitchen order");

        Long venueId = venueRepository.findByOwnerId(user.getId())
                .orElseThrow(()-> new RuntimeException("user don't have a venue")).getId();

        List<OrderStatus> hiddenStatuses = List.of(
                OrderStatus.COMPLETED,
                OrderStatus.CANCELLED
        );


        return orderMapper.toResponseList(orderRepository.findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(
                venueId,
                hiddenStatuses));
    }

    @Transactional(readOnly = true)
    public String getKaspiUrl(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("order not found"));

        Venue venue = order.getVenue();

        if (venue.getKaspiUrl() == null || venue.getKaspiUrl().isBlank()) {
            throw new RuntimeException("Заведение еще не добавило ссылку Kaspi");
        }

        return venue.getKaspiUrl();
    }

}
