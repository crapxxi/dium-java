package com.dium.demo.services;

import com.dium.demo.enums.OrderStatus;
import com.dium.demo.models.Order;
import com.dium.demo.repositories.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderExpirationService orderExpirationService;

    @Test
    void cancelExpiredOrders_WhenOrdersExist_ShouldCancelAndSave() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING);
        order.setComment("Оставить у двери");

        when(orderRepository.findAllByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        orderExpirationService.cancelExpiredOrders();

        ArgumentCaptor<List<Order>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).saveAll(listCaptor.capture());

        List<Order> savedOrders = listCaptor.getValue();
        assertThat(savedOrders).hasSize(1);

        Order savedOrder = savedOrders.get(0);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedOrder.getComment()).isEqualTo("[Авто-отмена: не подтвержден в течение 5 мин]");
    }

    @Test
    void cancelExpiredOrders_WhenNoOrdersExist_ShouldNotCallSaveAll() {
        when(orderRepository.findAllByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        orderExpirationService.cancelExpiredOrders();

        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void cancelExpiredOrders_ShouldUseCorrectTimeWindow() {
        when(orderRepository.findAllByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        orderExpirationService.cancelExpiredOrders();

        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findAllByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), timeCaptor.capture());

        LocalDateTime capturedTime = timeCaptor.getValue();
        LocalDateTime expectedTime = LocalDateTime.now().minusMinutes(5);

        assertThat(capturedTime).isBetween(expectedTime.minusSeconds(1), expectedTime.plusSeconds(1));
    }
}