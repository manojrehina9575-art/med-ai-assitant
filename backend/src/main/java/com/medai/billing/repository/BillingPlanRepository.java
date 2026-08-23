package com.medai.billing.repository;

import com.medai.billing.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, UUID> {

    Optional<BillingPlan> findByCode(String code);

    List<BillingPlan> findByIsActiveTrueOrderByPlatformFeeAsc();
}
