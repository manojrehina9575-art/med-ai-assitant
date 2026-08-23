package com.medai.compliance.consent.repository;

import com.medai.compliance.consent.entity.PatientConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<PatientConsent, UUID> {
    List<PatientConsent> findByPatientId(UUID patientId);
    Optional<PatientConsent> findByPatientIdAndPurpose(UUID patientId, String purpose);
    List<PatientConsent> findByTenantIdOrderByGrantedAtDesc(UUID tenantId);
    boolean existsByPatientIdAndPurposeAndStatus(UUID patientId, String purpose, String status);
}
