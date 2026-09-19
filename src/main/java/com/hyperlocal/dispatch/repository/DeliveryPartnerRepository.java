package com.hyperlocal.dispatch.repository;

import com.hyperlocal.dispatch.entity.DeliveryPartner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {
    Optional<DeliveryPartner> findByUserId(Long userId);
    List<DeliveryPartner> findByIsAvailableTrue();
}
