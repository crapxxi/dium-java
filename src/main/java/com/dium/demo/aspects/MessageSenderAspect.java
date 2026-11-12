package com.dium.demo.aspects;

import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.services.order.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

@Aspect
@Component
@EnableAsync
@RequiredArgsConstructor
public class MessageSenderAspect {
    private final OrderNotificationService notificationService;

    @AfterReturning(
            value = "execution(* com.dium.demo.services.order.OrderService.createOrder(..))",
            returning = "orderResponse")
    @Async
    public void sendMessageToVenueOwner(OrderResponse orderResponse) {
        notificationService.notifyVenueAboutNewOrder(orderResponse);
    }
}
