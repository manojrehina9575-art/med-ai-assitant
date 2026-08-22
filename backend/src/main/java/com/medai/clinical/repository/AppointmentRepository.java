package com.medai.clinical.repository;

import com.medai.clinical.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    Page<Appointment> findByTenantIdAndPatientIdOrderByScheduledAtDesc(UUID tenantId, UUID patientId, Pageable pageable);
    List<Appointment> findByTenantIdAndPatientId(UUID tenantId, UUID patientId);
    List<Appointment> findByTenantIdAndDoctorId(UUID tenantId, UUID doctorId);
}
