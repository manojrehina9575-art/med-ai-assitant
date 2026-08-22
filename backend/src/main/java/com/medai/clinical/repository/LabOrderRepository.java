package com.medai.clinical.repository;

import com.medai.clinical.entity.LabOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {
    Page<LabOrder> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, Pageable pageable);
    List<LabOrder> findByTenantIdAndPatientId(UUID tenantId, UUID patientId);
}
