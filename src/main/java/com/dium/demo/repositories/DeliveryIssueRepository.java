package com.dium.demo.repositories;

import com.dium.demo.enums.DeliveryIssueStatus;
import com.dium.demo.enums.DeliveryIssueType;
import com.dium.demo.models.DeliveryIssue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface DeliveryIssueRepository extends BaseRepository<DeliveryIssue, Long> {
    @EntityGraph(attributePaths = {"delivery", "delivery.order", "reporter", "resolvedBy"})
    @Query("""
            select i from DeliveryIssue i
            where (:status is null or i.status = :status)
            order by i.createdAt desc limit 100""")
    List<DeliveryIssue> findRecent(@Param("status") DeliveryIssueStatus status);

    boolean existsByDeliveryIdAndTypeAndStatus(Long deliveryId, DeliveryIssueType type, DeliveryIssueStatus status);

    /** Какие из доставок имеют открытые проблемы — чтобы подсветить их в админке. */
    @Query("""
            select distinct i.delivery.id from DeliveryIssue i
            where i.status = com.dium.demo.enums.DeliveryIssueStatus.OPEN and i.delivery.id in :deliveryIds""")
    Set<Long> findDeliveryIdsWithOpenIssues(@Param("deliveryIds") Collection<Long> deliveryIds);
}
