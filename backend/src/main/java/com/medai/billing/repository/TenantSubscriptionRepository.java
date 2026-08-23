package com.medai.billing.repository;

import com.medai.billing.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

    Optional<TenantSubscription> findByTenantId(UUID tenantId);

    /**
     * Every subscription due to be invoiced on this day of the month. Crosses tenants by design —
     * the caller opens maintenance access for the scan, then bills each tenant under its own
     * binding.
     */
    List<TenantSubscription> findByStatusAndBillingDay(String status, Short billingDay);
}
