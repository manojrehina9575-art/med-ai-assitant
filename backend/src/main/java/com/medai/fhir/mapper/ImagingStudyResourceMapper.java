package com.medai.fhir.mapper;

import com.medai.fhir.FhirConstants;
import com.medai.upload.entity.MedicalFile;
import com.medai.upload.enums.FileType;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * Maps an uploaded medical image to a FHIR ImagingStudy.
 *
 * <p>Honest about what it is: these studies were hand-uploaded, not pulled from a PACS, so there
 * is no DICOM Study Instance UID to carry unless the file happened to be DICOM. The resource
 * identifies the study by this deployment's own namespace and does not fabricate a UID — a fake
 * {@code urn:oid:} identifier is the sort of thing that corrupts a receiving PACS index.
 *
 * <p>Series and instance detail stays empty for the same reason. Filling it in properly is what
 * the DICOMweb integration is for; asserting one series of one instance from a filename would be
 * a guess presented as metadata.
 */
@Component
public class ImagingStudyResourceMapper {

    /**
     * DICOM modality codes for the imaging file types. Non-imaging types map to nothing rather
     * than to "OT" — a blood report is not an imaging study of unspecified modality, and
     * {@link #isImaging} keeps them out of this resource entirely.
     */
    private static final Map<FileType, String> MODALITY = Map.of(
            FileType.XRAY, "CR",
            FileType.CT_SCAN, "CT",
            FileType.ULTRASOUND, "US",
            FileType.MRI, "MR");

    public ImagingStudy toFhir(MedicalFile file) {
        ImagingStudy study = new ImagingStudy();
        study.setId(file.getId().toString());

        study.addIdentifier()
                .setSystem(FhirConstants.STUDY_SYSTEM)
                .setValue(file.getId().toString());

        study.setStatus(ImagingStudy.ImagingStudyStatus.AVAILABLE);
        study.setSubject(new Reference("Patient/" + file.getPatientId()));

        if (file.getCreatedAt() != null) {
            study.setStarted(Date.from(file.getCreatedAt()));
        }

        modality(file).ifPresent(code -> study.addModality(new Coding()
                .setSystem("http://dicom.nema.org/resources/ontology/DCM")
                .setCode(code)));

        if (file.getDescription() != null && !file.getDescription().isBlank()) {
            study.setDescription(file.getDescription());
        } else {
            study.setDescription(file.getOriginalFileName());
        }

        // Number of series and instances are required cardinality 0..1 but meaningless here; left
        // unset rather than asserted as 1.
        return study;
    }

    private java.util.Optional<String> modality(MedicalFile file) {
        return java.util.Optional.ofNullable(MODALITY.get(file.getFileType()));
    }

    /**
     * Whether this file is an imaging study at all.
     *
     * <p>A blood report or a discharge summary has a {@code MedicalFile} row like any other and is
     * emphatically not an ImagingStudy. Callers filter on this rather than producing a resource
     * with no modality and hoping the consumer notices.
     */
    public boolean isImaging(MedicalFile file) {
        return MODALITY.containsKey(file.getFileType());
    }
}
