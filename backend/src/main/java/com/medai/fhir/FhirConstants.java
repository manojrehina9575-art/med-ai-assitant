package com.medai.fhir;

/**
 * Identifier systems and profile URIs for the FHIR facade.
 *
 * <p>Identifier systems are namespaced per deployment rather than hard-coded to a vendor URL,
 * because two hospitals both issuing "MRN 12345" must not collide once records leave the building.
 *
 * <p>{@code ABDM_*} is where India's NDHM profiles attach. ABDM is FHIR R4 with its own profiles
 * and an ABHA-number identifier system; the mappers below produce base R4 resources, and claiming
 * an ABDM profile is a matter of stamping {@code meta.profile} and adding the ABHA identifier once
 * the HIP registration exists. Nothing here needs restructuring to get there.
 */
public final class FhirConstants {

    private FhirConstants() {
    }

    /** Base for this deployment's own identifier namespaces. Override per environment. */
    public static final String BASE_NAMESPACE = "urn:medai";

    public static final String MRN_SYSTEM = BASE_NAMESPACE + ":mrn";
    public static final String ANALYSIS_SYSTEM = BASE_NAMESPACE + ":analysis";
    public static final String STUDY_SYSTEM = BASE_NAMESPACE + ":study";

    /** India: ABHA (Ayushman Bharat Health Account) number, once ABDM linkage exists. */
    public static final String ABHA_SYSTEM = "https://healthid.abdm.gov.in/ns/abha-number";

    public static final String LOINC_SYSTEM = "http://loinc.org";
    public static final String ICD10_SYSTEM = "http://hl7.org/fhir/sid/icd-10";
    public static final String OBSERVATION_CATEGORY_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/observation-category";
    public static final String INTERPRETATION_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
    public static final String DIAGNOSTIC_SERVICE_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/v2-0074";
}
