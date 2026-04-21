package com.medai.upload.repository;

import com.medai.upload.entity.MedicalFile;
import com.medai.upload.enums.FileType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalFileRepository extends JpaRepository<MedicalFile, UUID> {

    Optional<MedicalFile> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MedicalFile> findByTenantIdAndPatientId(UUID tenantId, UUID patientId, Pageable pageable);

    List<MedicalFile> findByTenantIdAndPatientIdAndFileType(UUID tenantId, UUID patientId, FileType fileType);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndPatientId(UUID tenantId, UUID patientId);
}
