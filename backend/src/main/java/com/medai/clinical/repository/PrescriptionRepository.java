package com.medai.clinical.repository;

import com.medai.clinical.entity.Prescription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    Page<Prescription> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, Pageable pageable);
    List<Prescription> findByTenantIdAndPatientId(UUID tenantId, UUID patientId);
}
