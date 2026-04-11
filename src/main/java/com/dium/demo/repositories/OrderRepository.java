package com.dium.demo.repositories;

import com.dium.demo.enums.OrderStatus;
import com.dium.demo.models.ModifierGroup;
import com.dium.demo.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends BaseRepository<Order, Long> {
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByVenueIdOrderByCreatedAtDesc(Long venueId);
    List<Order> findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(
            Long venueId,
            List<OrderStatus> statusesToExclude
    );
    Optional<Order> findByPickupCode(Integer pickupCode);
    List<Order> findAllByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime time);
}
