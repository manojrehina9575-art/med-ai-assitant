package com.medai.fhir.mapper;

import com.medai.fhir.FhirConstants;
import com.medai.patient.entity.Patient;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Date;

/**
 * Maps a {@link Patient} to a FHIR R4 Patient, and its allergy list to AllergyIntolerance.
 *
 * <p>Allergies live on the patient row as free text, which is fine internally and meaningless to
 * anyone else. FHIR models them as separate AllergyIntolerance resources, and that is the form a
 * receiving system — or an ABDM care-context bundle — expects, so they are exposed that way rather
 * than smuggled into an extension on Patient.
 */
@Component
public class PatientResourceMapper {

    public org.hl7.fhir.r4.model.Patient toFhir(Patient source) {
        org.hl7.fhir.r4.model.Patient patient = new org.hl7.fhir.r4.model.Patient();
        patient.setId(source.getId().toString());

        patient.addIdentifier()
                .setSystem(FhirConstants.MRN_SYSTEM)
                .setValue(source.getMedicalRecordNumber())
                .setUse(Identifier.IdentifierUse.USUAL)
                .setType(new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                        .setCode("MR")
                        .setDisplay("Medical record number")));

        patient.addName()
                .setUse(HumanName.NameUse.OFFICIAL)
                .setFamily(source.getLastName())
                .addGiven(source.getFirstName());

        patient.setGender(gender(source.getGender()));

        if (source.getDateOfBirth() != null) {
            patient.setBirthDate(Date.from(source.getDateOfBirth().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        if (source.getPhone() != null && !source.getPhone().isBlank()) {
            patient.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(source.getPhone())
                    .setUse(ContactPoint.ContactPointUse.MOBILE);
        }
        if (source.getEmail() != null && !source.getEmail().isBlank()) {
            patient.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(source.getEmail());
        }

        if (source.getAddress() != null && !source.getAddress().isBlank()) {
            // Stored as one free-text field, so it maps to Address.text rather than being split
            // into line/city/postalCode by guesswork.
            patient.addAddress().setText(source.getAddress());
        }

        if (source.getEmergencyContactName() != null && !source.getEmergencyContactName().isBlank()) {
            org.hl7.fhir.r4.model.Patient.ContactComponent contact = patient.addContact();
            contact.setName(new HumanName().setText(source.getEmergencyContactName()));
            contact.addRelationship().addCoding(new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v2-0131")
                    .setCode("C")
                    .setDisplay("Emergency Contact"));
            if (source.getEmergencyContactPhone() != null) {
                contact.addTelecom()
                        .setSystem(ContactPoint.ContactPointSystem.PHONE)
                        .setValue(source.getEmergencyContactPhone());
            }
        }

        patient.setActive(source.getIsActive() == null || source.getIsActive());
        return patient;
    }

    /**
     * One AllergyIntolerance per documented allergy.
     *
     * <p>Deliberately uncoded: the source is free text, and mapping "penicillin" to a SNOMED
     * substance concept by string match is the kind of guess that turns a note into a false
     * assertion. {@code code.text} carries what was actually recorded.
     */
    public AllergyIntolerance toAllergyIntolerance(Patient source, String allergyText, int index) {
        AllergyIntolerance allergy = new AllergyIntolerance();
        allergy.setId(source.getId() + "-allergy-" + index);

        allergy.setClinicalStatus(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical")
                .setCode("active")));

        // "unconfirmed" is the honest verification status for a free-text entry with no recorded
        // reaction history behind it.
        allergy.setVerificationStatus(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification")
                .setCode("unconfirmed")));

        allergy.setCode(new CodeableConcept().setText(allergyText));
        allergy.setPatient(new Reference("Patient/" + source.getId()));
        return allergy;
    }

    private Enumerations.AdministrativeGender gender(com.medai.patient.enums.Gender gender) {
        if (gender == null) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        return switch (gender) {
            case MALE -> Enumerations.AdministrativeGender.MALE;
            case FEMALE -> Enumerations.AdministrativeGender.FEMALE;
            default -> Enumerations.AdministrativeGender.OTHER;
        };
    }
}
