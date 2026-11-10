package com.dium.demo.services.order;

import com.dium.demo.enums.OrderStatus;
import com.dium.demo.models.Order;
import com.dium.demo.repositories.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationService {

    private final OrderRepository orderRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredOrders() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(5);

        List<Order> expiredOrders = orderRepository.findAllByStatusAndCreatedAtBefore(
                OrderStatus.PENDING,
                expirationTime
        );

        if (!expiredOrders.isEmpty()) {
            log.info("Found {} expired orders for cancel", expiredOrders.size());

            expiredOrders.forEach(order -> {
                order.setStatus(OrderStatus.CANCELLED);
                order.setComment("[Auto-expiration: didn't confirmed in 5 minutes]");
            });

            orderRepository.saveAll(expiredOrders);
        }
    }
}