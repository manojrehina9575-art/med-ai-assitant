package com.medai.patient.service;

import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.patient.dto.CreatePatientRequest;
import com.medai.patient.dto.PatientResponse;
import com.medai.patient.dto.UpdatePatientRequest;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (patientRepository.existsByTenantIdAndMedicalRecordNumber(tenantId, request.getMedicalRecordNumber())) {
            throw new BadRequestException("Patient with MRN already exists: " + request.getMedicalRecordNumber());
        }

        Patient patient = Patient.builder()
                .medicalRecordNumber(request.getMedicalRecordNumber())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .bloodGroup(request.getBloodGroup())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .medicalHistory(request.getMedicalHistory())
                .allergies(request.getAllergies())
                .build();
        patient.setTenantId(tenantId);
        patient = patientRepository.save(patient);

        return toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PatientResponse getPatient(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
        return toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PatientResponse> listPatients(int page, int size, String search) {
        UUID tenantId = TenantContext.requireTenantId();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Patient> patients;
        if (search != null && !search.isBlank()) {
            patients = patientRepository.searchByTenantId(tenantId, search.trim(), pageRequest);
        } else {
            patients = patientRepository.findByTenantId(tenantId, pageRequest);
        }

        return PagedResponse.<PatientResponse>builder()
                .content(patients.getContent().stream().map(this::toResponse).toList())
                .page(patients.getNumber())
                .size(patients.getSize())
                .totalElements(patients.getTotalElements())
                .totalPages(patients.getTotalPages())
                .last(patients.isLast())
                .build();
    }

    @Transactional
    public PatientResponse updatePatient(UUID patientId, UpdatePatientRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));

        if (request.getFirstName() != null) patient.setFirstName(request.getFirstName());
        if (request.getLastName() != null) patient.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null) patient.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getBloodGroup() != null) patient.setBloodGroup(request.getBloodGroup());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getEmail() != null) patient.setEmail(request.getEmail());
        if (request.getAddress() != null) patient.setAddress(request.getAddress());
        if (request.getEmergencyContactName() != null) patient.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        if (request.getMedicalHistory() != null) patient.setMedicalHistory(request.getMedicalHistory());
        if (request.getAllergies() != null) patient.setAllergies(request.getAllergies());

        patient = patientRepository.save(patient);
        return toResponse(patient);
    }

    @Transactional
    public void deletePatient(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId));
        patient.setIsActive(false);
        patientRepository.save(patient);
    }

    private PatientResponse toResponse(Patient p) {
        return PatientResponse.builder()
                .id(p.getId())
                .tenantId(p.getTenantId())
                .medicalRecordNumber(p.getMedicalRecordNumber())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .fullName(p.getFullName())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .bloodGroup(p.getBloodGroup())
                .phone(p.getPhone())
                .email(p.getEmail())
                .address(p.getAddress())
                .emergencyContactName(p.getEmergencyContactName())
                .emergencyContactPhone(p.getEmergencyContactPhone())
                .medicalHistory(p.getMedicalHistory())
                .allergies(p.getAllergies())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
