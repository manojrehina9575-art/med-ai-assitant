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

    /**
     * Base for this deployment's own identifier namespaces.
     *
     * <p>A URL on a domain we own, not a bare {@code urn:} — FHIR expects an identifier system to
     * be a globally unique URI, and convention is that it resolves to something describing the
     * namespace. A receiving EHR uses this string to decide whether two identifiers refer to the
     * same thing, so it must never collide with another vendor's.
     *
     * <p>Changing this after a hospital has ingested resources means their record now holds
     * identifiers under a namespace that no longer matches ours, and the two sets stop reconciling.
     * It is fixed here, before anything has integrated, and should not move again.
     */
    public static final String BASE_NAMESPACE = "https://medaiclinical.com/ns";

    public static final String MRN_SYSTEM = BASE_NAMESPACE + "/mrn";
    public static final String ANALYSIS_SYSTEM = BASE_NAMESPACE + "/analysis";
    public static final String STUDY_SYSTEM = BASE_NAMESPACE + "/study";

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
