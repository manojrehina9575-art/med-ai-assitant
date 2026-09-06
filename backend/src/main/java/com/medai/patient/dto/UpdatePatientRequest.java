package com.medai.patient.dto;

import com.medai.patient.enums.Gender;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UpdatePatientRequest {

    private String medicalRecordNumber;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String bloodGroup;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private List<String> medicalHistory;
    private List<String> allergies;
    private Boolean isActive;
}

