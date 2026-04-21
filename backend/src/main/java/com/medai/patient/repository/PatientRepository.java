package com.medai.patient.repository;

import com.medai.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Patient> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Patient> findByTenantIdAndMedicalRecordNumber(UUID tenantId, String mrn);

    boolean existsByTenantIdAndMedicalRecordNumber(UUID tenantId, String mrn);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND " +
           "(LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.medicalRecordNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Patient> searchByTenantId(@Param("tenantId") UUID tenantId,
                                   @Param("query") String query,
                                   Pageable pageable);

    long countByTenantId(UUID tenantId);
}
