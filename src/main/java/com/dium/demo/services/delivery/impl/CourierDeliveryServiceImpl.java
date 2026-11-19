package com.dium.demo.services.delivery.impl;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.ConflictException;
import com.dium.demo.exceptions.InvalidHandoverCodeException;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.User;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.repositories.UserRepository;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.delivery.CourierDeliveryService;
import com.dium.demo.services.delivery.DeliveryViews;
import com.dium.demo.services.delivery.DeliveryWorkflow;
import com.dium.demo.services.delivery.HandoverCode;
import com.dium.demo.services.delivery.HandoverCodeVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Курьерами работают студенты — пользователи с ролью CLIENT. */
@Service
@RequiredArgsConstructor
public class CourierDeliveryServiceImpl implements CourierDeliveryService {
    private static final String TAKEN_BY_OTHER = "Этот заказ уже взял другой курьер";

    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final DeliveryWorkflow workflow;
    private final HandoverCodeVerifier codeVerifier;
    private final DeliveryViews views;
    private final DeliveryProperties properties;

    @RequireRole(UserRole.CLIENT)
    @Transactional(readOnly = true)
    @Override
    public List<DeliveryResponses.Available> getAvailable() {
        User courier = userDetailsService.getCurrentUser();
        return deliveryRepository.findAvailableFor(courier.getId()).stream().map(views::available).toList();
    }

    @RequireRole(UserRole.CLIENT)
    @Transactional
    @Override
    public DeliveryResponses.Courier accept(Long deliveryId) {
        // Блокировка строки курьера: два его одновременных запроса не обойдут лимит активных доставок
        User courier = userRepository.findByIdForUpdate(userDetailsService.getCurrentUser().getId())
                .orElseThrow(() -> new BusinessLogicException("User not found"));
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);

        if (delivery.getStatus() != DeliveryStatus.SEARCHING_COURIER) {
            throw new ConflictException(TAKEN_BY_OTHER);
        }
        if (delivery.getOrder().getUser().getId().equals(courier.getId())) {
            throw new BusinessLogicException("Нельзя доставлять собственный заказ");
        }
        long active = deliveryRepository.countByCourierIdAndStatusIn(courier.getId(), DeliveryStatus.IN_PROGRESS);
        if (active >= properties.maxActivePerCourier()) {
            throw new BusinessLogicException(
                    "Можно вести не больше " + properties.maxActivePerCourier() + " доставок одновременно");
        }

        workflow.assign(delivery, courier);
        try {
            // Сразу в БД: если другой курьер успел раньше, @Version вернёт конфликт здесь, а не при коммите
            deliveryRepository.saveAndFlush(delivery);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException(TAKEN_BY_OTHER);
        }
        return views.forCourier(delivery);
    }

    @RequireRole(UserRole.CLIENT)
    @Transactional
    @Override
    public DeliveryResponses.Courier release(Long deliveryId) {
        Delivery delivery = assignedToCurrentCourier(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new BusinessLogicException("Заказ уже у вас на руках — его нужно довезти. Если что-то случилось, сообщите о проблеме");
        }
        workflow.release(delivery);
        return views.forCourier(delivery);
    }

    @RequireRole(UserRole.CLIENT)
    @Transactional(noRollbackFor = InvalidHandoverCodeException.class)
    @Override
    public DeliveryResponses.Courier confirmDelivered(Long deliveryId, String customerCode) {
        Delivery delivery = assignedToCurrentCourier(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new BusinessLogicException("Сначала заберите заказ в заведении");
        }
        codeVerifier.verify(delivery, HandoverCode.DELIVERY, customerCode);
        workflow.deliver(delivery);
        return views.forCourier(delivery);
    }

    @RequireRole(UserRole.CLIENT)
    @Transactional(readOnly = true)
    @Override
    public List<DeliveryResponses.Courier> getMyDeliveries() {
        User courier = userDetailsService.getCurrentUser();
        return deliveryRepository.findTop50ByCourierIdOrderByCreatedAtDesc(courier.getId()).stream()
                .map(views::forCourier)
                .toList();
    }

    @RequireRole(UserRole.CLIENT)
    @Transactional(readOnly = true)
    @Override
    public DeliveryResponses.CourierSummary getSummary() {
        Long courierId = userDetailsService.getCurrentUser().getId();
        return new DeliveryResponses.CourierSummary(
                deliveryRepository.countByCourierIdAndStatus(courierId, DeliveryStatus.DELIVERED),
                deliveryRepository.sumEarnedBy(courierId),
                properties.fee(),
                deliveryRepository.countByCourierIdAndStatusIn(courierId, DeliveryStatus.IN_PROGRESS),
                properties.maxActivePerCourier());
    }

    /**
     * Доставка текущего курьера. Чужая — 422, а не 403: фронтенд выходит из аккаунта на 403,
     * а сюда легко попасть честно — например, если администратор снял курьера, пока у того открыта страница.
     */
    private Delivery assignedToCurrentCourier(Long deliveryId) {
        User courier = userDetailsService.getCurrentUser();
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        if (delivery.getCourier() == null || !delivery.getCourier().getId().equals(courier.getId())) {
            throw new BusinessLogicException("Эта доставка больше не закреплена за вами");
        }
        return delivery;
    }
}
