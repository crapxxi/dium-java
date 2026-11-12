package com.dium.demo.repositories;

import com.dium.demo.enums.OrderStatus;
import com.dium.demo.models.Order;
import com.dium.demo.security.access.OwnerIdRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends BaseRepository<Order, Long>, OwnerIdRepository {
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByVenueIdAndStatusNotInOrderByCreatedAtDesc(
            Long venueId,
            List<OrderStatus> statusesToExclude
    );
    List<Order> findAllByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime time);

    @Override
    @Query("select o.venue.owner.id from Order o where o.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);
}
