package com.dium.demo.services.delivery.impl;

import com.dium.demo.config.DeliveryProperties;
import com.dium.demo.dto.responses.DeliveryResponses;
import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.BusinessLogicException;
import com.dium.demo.models.Delivery;
import com.dium.demo.models.DeliveryIssue;
import com.dium.demo.repositories.DeliveryIssueRepository;
import com.dium.demo.repositories.DeliveryRepository;
import com.dium.demo.security.access.RequireRole;
import com.dium.demo.services.auth.CustomUserDetailsService;
import com.dium.demo.services.delivery.AdminDeliveryService;
import com.dium.demo.services.delivery.DeliveryCodes;
import com.dium.demo.services.delivery.DeliveryViews;
import com.dium.demo.services.delivery.DeliveryWorkflow;
import com.dium.demo.services.delivery.HandoverCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminDeliveryServiceImpl implements AdminDeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryIssueRepository issueRepository;
    private final CustomUserDetailsService userDetailsService;
    private final DeliveryWorkflow workflow;
    private final DeliveryCodes codes;
    private final DeliveryViews views;
    private final DeliveryProperties properties;
    private final Clock clock;

    @RequireRole(UserRole.ADMIN)
    @Transactional(readOnly = true)
    @Override
    public List<DeliveryResponses.Admin> getDeliveries(DeliveryStatus status) {
        List<Delivery> deliveries = deliveryRepository.findRecent(status);
        if (deliveries.isEmpty()) {
            return List.of();
        }
        Set<Long> withIssues = issueRepository.findDeliveryIdsWithOpenIssues(
                deliveries.stream().map(Delivery::getId).toList());
        return deliveries.stream()
                .map(delivery -> views.forAdmin(delivery, withIssues.contains(delivery.getId())))
                .toList();
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional(readOnly = true)
    @Override
    public List<DeliveryResponses.Issue> getIssues(DeliveryIssueStatus status) {
        return issueRepository.findRecent(status).stream().map(views::issue).toList();
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional
    @Override
    public DeliveryResponses.Admin reassign(Long deliveryId, String reason) {
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new BusinessLogicException("Снять курьера можно только до того, как он забрал заказ");
        }
        workflow.release(delivery);
        note(delivery, "Курьер снят", reason);
        return adminView(delivery);
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional
    @Override
    public DeliveryResponses.Admin cancel(Long deliveryId, String reason) {
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        if (!delivery.getStatus().isActive()) {
            throw new BusinessLogicException("Доставка уже завершена");
        }
        workflow.cancel(delivery, reason);
        note(delivery, "Доставка отменена", reason);
        return adminView(delivery);
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional
    @Override
    public DeliveryResponses.Admin complete(Long deliveryId, String reason) {
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        if (!DeliveryStatus.IN_PROGRESS.contains(delivery.getStatus())) {
            throw new BusinessLogicException("Завершить можно только доставку, у которой есть курьер");
        }
        workflow.forceDeliver(delivery);
        note(delivery, "Завершено администратором", reason);
        return adminView(delivery);
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional
    @Override
    public DeliveryResponses.Admin unlockCodes(Long deliveryId, String reason) {
        Delivery delivery = deliveryRepository.findByIdOrThrow(deliveryId);
        if (!delivery.getStatus().isActive()) {
            throw new BusinessLogicException("Доставка уже завершена");
        }
        int max = properties.maxCodeAttempts();
        // Код, который пытались подобрать, мог быть подсмотрен или угадан наполовину — выдаём новый
        if (HandoverCode.PICKUP.isLocked(delivery, max) && delivery.getPickupCode() != null) {
            delivery.setPickupCode(codes.newSealedCode());
        }
        if (HandoverCode.DELIVERY.isLocked(delivery, max)) {
            delivery.setDeliveryCode(codes.newSealedCode());
        }
        delivery.setPickupCodeAttempts(0);
        delivery.setDeliveryCodeAttempts(0);
        note(delivery, "Ввод кодов разблокирован", reason);
        return adminView(delivery);
    }

    @RequireRole(UserRole.ADMIN)
    @Transactional
    @Override
    public DeliveryResponses.Issue resolveIssue(Long issueId, String resolution) {
        DeliveryIssue issue = issueRepository.findByIdOrThrow(issueId);
        if (issue.getStatus() == DeliveryIssueStatus.RESOLVED) {
            throw new BusinessLogicException("Проблема уже решена");
        }
        issue.setStatus(DeliveryIssueStatus.RESOLVED);
        issue.setResolution(resolution.trim());
        issue.setResolvedBy(userDetailsService.getCurrentUser());
        issue.setResolvedAt(clock.instant());
        return views.issue(issue);
    }

    private DeliveryResponses.Admin adminView(Delivery delivery) {
        boolean hasOpenIssue = !issueRepository.findDeliveryIdsWithOpenIssues(List.of(delivery.getId())).isEmpty();
        return views.forAdmin(delivery, hasOpenIssue);
    }

    private void note(Delivery delivery, String action, String reason) {
        String admin = userDetailsService.getCurrentUser().getName();
        String text = action + " (" + admin + "): " + reason.trim();
        delivery.setAdminNote(text.length() > 600 ? text.substring(0, 600) : text);
    }
}
