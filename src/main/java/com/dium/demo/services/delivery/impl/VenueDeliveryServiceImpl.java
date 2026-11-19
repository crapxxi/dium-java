package com.dium.demo.services.delivery.impl;

import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.exceptions.InvalidHandoverCodeException;
import com.dium.demo.models.Delivery;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.security.access.CheckOwnership;
import com.dium.demo.security.access.OwnedResource;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.delivery.DeliveryViews;
import com.dium.demo.services.delivery.DeliveryWorkflow;
import com.dium.demo.services.delivery.HandoverCode;
import com.dium.demo.services.delivery.HandoverCodeVerifier;
import com.dium.demo.services.delivery.VenueDeliveryService;
import com.dium.demo.services.venue.CurrentVenueProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueDeliveryServiceImpl implements VenueDeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final CurrentVenueProvider currentVenueProvider;
    private final DeliveryWorkflow workflow;
    private final HandoverCodeVerifier codeVerifier;
    private final DeliveryViews views;

    @RequireRole(UserRole.VENUE_OWNER)
    @Transactional(readOnly = true)
    @Override
    public List<DeliveryResponses.Venue> getVenueDeliveries() {
        Long venueId = currentVenueProvider.getCurrentVenue().getId();
        return deliveryRepository.findRecentForVenue(venueId).stream().map(views::forVenue).toList();
    }

    @RequireRole(UserRole.VENUE_OWNER)
    @CheckOwnership(OwnedResource.DELIVERY)
    @Transactional(noRollbackFor = InvalidHandoverCodeException.class)
    @Override
    public DeliveryResponses.Venue confirmPickup(Long deliveryId, String courierCode) {
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new BusinessLogicException(switch (delivery.getStatus()) {
                case SEARCHING_COURIER -> "Курьер ещё не назначен";
                case CANCELLED -> "Доставка отменена";
                default -> "Заказ уже передан курьеру";
            });
        }
        codeVerifier.verify(delivery, HandoverCode.PICKUP, courierCode);
        workflow.pickUp(delivery);
        return views.forVenue(delivery);
    }
}
