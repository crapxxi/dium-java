package com.dium.demo.services.delivery.impl;

import com.dium.demo.dto.requests.DeliveryIssueRequest;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.enums.DeliveryParticipant;
import com.dium.demo.exceptions.AccessDeniedException;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.DeliveryIssue;
import com.dium.demo.models.User;
import com.dium.demo.repositories.DeliveryIssueRepository;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.delivery.DeliveryIssueService;
import com.dium.demo.services.delivery.DeliveryViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeliveryIssueServiceImpl implements DeliveryIssueService {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryIssueRepository issueRepository;
    private final CustomUserDetailsService userDetailsService;
    private final DeliveryViews views;
    private final Clock clock;

    @Transactional
    @Override
    public DeliveryResponses.Issue report(Long deliveryId, DeliveryIssueRequest request) {
        if (request.type() == DeliveryIssueType.CODE_LOCKED) {
            throw new BusinessLogicException("Эту проблему создаёт система");
        }
        User reporter = userDetailsService.getCurrentUser();
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        DeliveryParticipant participant = participantOf(delivery, reporter)
                .orElseThrow(() -> new AccessDeniedException("Сообщить о проблеме может только участник доставки"));

        DeliveryIssue issue = issueRepository.save(DeliveryIssue.builder()
                .delivery(delivery)
                .reporter(reporter)
                .reportedBy(participant)
                .type(request.type())
                .description(blankToNull(request.description()))
                .createdAt(clock.instant())
                .build());
        return views.issue(issue);
    }

    @Override
    public void openSystemIssue(Delivery delivery, DeliveryIssueType type, String description) {
        if (issueRepository.existsByDeliveryIdAndTypeAndStatus(delivery.getId(), type, DeliveryIssueStatus.OPEN)) {
            return;
        }
        issueRepository.save(DeliveryIssue.builder()
                .delivery(delivery)
                .reportedBy(DeliveryParticipant.SYSTEM)
                .type(type)
                .description(description)
                .createdAt(clock.instant())
                .build());
    }

    /** Кем пользователь приходится доставке. Бывший курьер (снятый с заказа) участником уже не считается. */
    private static Optional<DeliveryParticipant> participantOf(Delivery delivery, User user) {
        Long userId = user.getId();
        if (delivery.getOrder().getUser().getId().equals(userId)) {
            return Optional.of(DeliveryParticipant.CUSTOMER);
        }
        if (delivery.getCourier() != null && delivery.getCourier().getId().equals(userId)) {
            return Optional.of(DeliveryParticipant.COURIER);
        }
        User owner = delivery.getOrder().getVenue().getOwner();
        if (owner != null && owner.getId().equals(userId)) {
            return Optional.of(DeliveryParticipant.VENUE);
        }
        return Optional.empty();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
