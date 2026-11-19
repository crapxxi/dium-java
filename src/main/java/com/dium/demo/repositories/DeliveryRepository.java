package com.dium.demo.repositories;

import com.dium.demo.enums.DeliveryStatus;
import com.dium.demo.models.Delivery;
import com.dium.demo.security.access.OwnerIdRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends BaseRepository<Delivery, Long>, OwnerIdRepository {
    Optional<Delivery> findByOrderId(Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<DeliveryStatus> statuses);

    /** Свободные заказы для курьера: без его собственных, самые старые первыми. */
    @EntityGraph(attributePaths = {"order", "order.venue"})
    @Query("""
            select d from Delivery d
            where d.status = com.dium.demo.enums.DeliveryStatus.SEARCHING_COURIER
              and d.order.user.id <> :courierId
            order by d.createdAt asc""")
    List<Delivery> findAvailableFor(@Param("courierId") Long courierId);

    @EntityGraph(attributePaths = {"order", "order.venue", "order.user"})
    List<Delivery> findTop50ByCourierIdOrderByCreatedAtDesc(Long courierId);

    long countByCourierIdAndStatusIn(Long courierId, Collection<DeliveryStatus> statuses);

    long countByCourierIdAndStatus(Long courierId, DeliveryStatus status);

    /** Заработок курьера: сумма оплат только за доставленные заказы. */
    @Query("""
            select coalesce(sum(d.courierFee), 0) from Delivery d
            where d.courier.id = :courierId and d.status = com.dium.demo.enums.DeliveryStatus.DELIVERED""")
    BigDecimal sumEarnedBy(@Param("courierId") Long courierId);

    @EntityGraph(attributePaths = {"order", "order.venue", "courier"})
    @Query("select d from Delivery d where d.order.user.id = :customerId order by d.createdAt desc limit 20")
    List<Delivery> findRecentForCustomer(@Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"order", "courier"})
    @Query("select d from Delivery d where d.order.venue.id = :venueId order by d.createdAt desc limit 50")
    List<Delivery> findRecentForVenue(@Param("venueId") Long venueId);

    @EntityGraph(attributePaths = {"order", "order.venue", "order.user", "courier"})
    @Query("""
            select d from Delivery d
            where (:status is null or d.status = :status)
            order by d.createdAt desc limit 100""")
    List<Delivery> findRecent(@Param("status") DeliveryStatus status);

    @Override
    @Query("select d.order.venue.owner.id from Delivery d where d.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);
}
