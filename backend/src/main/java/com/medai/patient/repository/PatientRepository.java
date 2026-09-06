package com.medai.patient.repository;

import com.medai.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Patient> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<Patient> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Patient> findByTenantIdAndIsActive(UUID tenantId, boolean isActive, Pageable pageable);

    Optional<Patient> findByTenantIdAndMedicalRecordNumber(UUID tenantId, String mrn);

    boolean existsByTenantIdAndMedicalRecordNumber(UUID tenantId, String mrn);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND " +
           "(LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.medicalRecordNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Patient> searchByTenantId(@Param("tenantId") UUID tenantId,
                                   @Param("query") String query,
                                   Pageable pageable);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.isActive = :isActive AND " +
           "(LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.medicalRecordNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Patient> searchByTenantIdAndIsActive(@Param("tenantId") UUID tenantId,
                                             @Param("query") String query,
                                             @Param("isActive") boolean isActive,
                                             Pageable pageable);

    long countByTenantId(UUID tenantId);

    /** Batch lookup, so a list of N rows referencing patients costs one query instead of N. */
    List<Patient> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}

