package com.dium.demo.services;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.mappers.OrderMapper;
import com.dium.demo.models.*;
import com.dium.demo.repositories.ModifierRepository;
import com.dium.demo.repositories.OrderRepository;
import com.dium.demo.repositories.ProductRepository;
import com.dium.demo.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ModifierRepository modifierRepository;
    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Venue venue;
    private Product product;
    private Modifier modifier;
    private OrderResponse dummyResponse;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setRole(UserRole.VENUE_OWNER);
        user.setPaymentFrom("Card");

        venue = new Venue();
        venue.setId(1L);
        venue.setOwner(user);
        venue.setIsWorking(true);
        venue.setCanDeliver(true);
        venue.setDeliveryPrice(BigDecimal.valueOf(500));
        venue.setKaspiUrl("https://kaspi.kz/test");

        product = new Product();
        product.setId(1L);
        product.setVenue(venue);
        product.setPrice(BigDecimal.valueOf(1000));
        product.setInStock(true);
        product.setName("Test Product");

        ModifierGroup group = new ModifierGroup();
        group.setId(1L);
        group.setProduct(product);

        modifier = new Modifier();
        modifier.setId(1L);
        modifier.setModifierGroup(group);
        modifier.setPriceDelta(BigDecimal.valueOf(200));

        dummyResponse = new OrderResponse(
                1L, "Test Venue", BigDecimal.valueOf(2900), OrderStatus.PENDING,
                1234, LocalDateTime.now(), new ArrayList<>(), "Test Address",
                "Comment", BigDecimal.valueOf(500), "Card", "8777777777"
        );
    }

    @Test
    void createOrder_Success_WithDeliveryAndModifiers() {
        OrderItemRequest itemRequest = new OrderItemRequest(1L, 2, List.of(1L));
        OrderRequest request = new OrderRequest(1L, List.of(itemRequest), "Test Address", "Comment", BigDecimal.valueOf(500), "Card");

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(venueRepository.findByIdOrThrow(1L)).thenReturn(venue);
        when(productRepository.findByIdOrThrow(1L)).thenReturn(product);
        when(modifierRepository.findAllById(List.of(1L))).thenReturn(List.of(modifier));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(orderMapper.toResponse(any(Order.class))).thenReturn(dummyResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isNotNull();
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());

        Order capturedOrder = orderCaptor.getAllValues().get(0); // Берем первый save (до генерации пин-кода)
        assertThat(capturedOrder.getTotalSum()).isEqualByComparingTo(BigDecimal.valueOf(2900));
        assertThat(capturedOrder.getDeliveryFee()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void createOrder_VenueNotWorking_ThrowsException() {
        venue.setIsWorking(false);
        OrderRequest request = new OrderRequest(1L, new ArrayList<>(), "Addr", "Comm", BigDecimal.ZERO, "Card");

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(venueRepository.findByIdOrThrow(1L)).thenReturn(venue);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessage("Venue is not working");
    }

    @Test
    void createOrder_ProductNotInStock_ThrowsException() {
        product.setInStock(false);
        OrderItemRequest itemRequest = new OrderItemRequest(1L, 1, new ArrayList<>());
        OrderRequest request = new OrderRequest(1L, List.of(itemRequest), "Addr", "Comm", BigDecimal.ZERO, "Card");

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(venueRepository.findByIdOrThrow(1L)).thenReturn(venue);
        when(productRepository.findByIdOrThrow(1L)).thenReturn(product);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessageContaining("is not in stock");
    }

    @Test
    void updateStatus_PendingToPreparing_Success() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING);
        order.setVenue(venue);

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdOrThrow(1L)).thenReturn(order);

        orderService.updateStatus(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    void updateStatus_Completed_ThrowsException() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED);
        order.setVenue(venue);

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdOrThrow(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateStatus(1L))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessage("Wrong status type");
    }

    @Test
    void cancelOrder_Pending_Success() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING);
        order.setVenue(venue);

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdOrThrow(1L)).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_NotPending_ThrowsException() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PREPARING);
        order.setVenue(venue);

        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(orderRepository.findByIdOrThrow(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessLogicException.class)
                .hasMessage("Order is accepted, can't cancel");
    }

    @Test
    void getOrdersByUserId_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(orderRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(new Order()));
        when(orderMapper.toResponseList(anyList())).thenReturn(List.of(dummyResponse));

        List<OrderResponse> result = orderService.getOrdersByUserId();

        assertThat(result).hasSize(1);
        verify(orderRepository).findAllByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void getKitchenOrders_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(orderRepository.findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(eq(1L), anyList()))
                .thenReturn(List.of(new Order()));
        when(orderMapper.toResponseList(anyList())).thenReturn(List.of(dummyResponse));

        List<OrderResponse> result = orderService.getKitchenOrders();

        assertThat(result).hasSize(1);
    }

    @Test
    void getOrderHistory_Success() {
        when(userDetailsService.getCurrentUser()).thenReturn(user);
        when(venueRepository.findByOwner_Id(1L)).thenReturn(Optional.of(venue));
        when(orderRepository.findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(eq(1L), anyList()))
                .thenReturn(List.of(new Order()));
        when(orderMapper.toResponseList(anyList())).thenReturn(List.of(dummyResponse));

        List<OrderResponse> result = orderService.getOrderHistory();

        assertThat(result).hasSize(1);
    }

    @Test
    void getKaspiUrl_Success() {
        Order order = new Order();
        order.setVenue(venue);

        when(orderRepository.findByIdOrThrow(1L)).thenReturn(order);

        String result = orderService.getKaspiUrl(1L);

        assertThat(result).isEqualTo("https://kaspi.kz/test");
    }

    @Test
    void getKaspiUrl_NullUrl_ThrowsException() {
        venue.setKaspiUrl(null);
        Order order = new Order();
        order.setVenue(venue);

        when(orderRepository.findByIdOrThrow(1L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.getKaspiUrl(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Заведение еще не добавило ссылку Kaspi");
    }
}