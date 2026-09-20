package com.hyperlocal.dispatch.repository;

import com.hyperlocal.dispatch.entity.DeliveryAssignment;
import com.hyperlocal.dispatch.enums.DeliveryAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    Optional<DeliveryAssignment> findFirstByDeliveryIdOrderByCreatedAtDesc(Long deliveryId);

    List<DeliveryAssignment> findByDeliveryId(Long deliveryId);

    Optional<DeliveryAssignment> findByDeliveryIdAndStatus(Long deliveryId, DeliveryAssignmentStatus status);
}
