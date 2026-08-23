package com.medai.fhir.mapper;

import com.medai.analysis.dto.BloodReportResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.fhir.FhirConstants;
import com.medai.terminology.service.LoincMapper;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps extracted blood-report parameters to FHIR Observations.
 *
 * <p>One Observation per analyte, LOINC-coded where {@link LoincMapper} recognises it. That coding
 * is the difference between a resource another system can trend and a string it can only display:
 * two labs writing "Hb" and "Haemoglobin" produce the same LOINC 718-7, and a receiving EMR can
 * put them on one graph.
 *
 * <p>Where the analyte is unrecognised the Observation carries {@code code.text} and no coding.
 * That is a deliberate choice over guessing: a wrong LOINC is worse than an absent one, because
 * the consumer trusts it.
 */
@Component
@RequiredArgsConstructor
public class ObservationResourceMapper {

    private final LoincMapper loincMapper;

    /** "4.5-11.0", "4.5 - 11.0", "<200", "> 40" — the shapes reference ranges actually arrive in. */
    private static final Pattern RANGE = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)\\s*[-–]\\s*(\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern UPPER_ONLY = Pattern.compile("^\\s*[<≤]\\s*(\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern LOWER_ONLY = Pattern.compile("^\\s*[>≥]\\s*(\\d+(?:\\.\\d+)?)\\s*$");

    public Observation toFhir(AnalysisRequest analysis, BloodReportResultDto.Parameter parameter, int index) {
        Observation observation = new Observation();
        observation.setId(analysis.getId() + "-obs-" + index);

        observation.setStatus(observationStatus(analysis));

        observation.addCategory().addCoding(new Coding()
                .setSystem(FhirConstants.OBSERVATION_CATEGORY_SYSTEM)
                .setCode("laboratory")
                .setDisplay("Laboratory"));

        CodeableConcept code = new CodeableConcept().setText(parameter.getName());
        Optional<LoincMapper.Loinc> loinc = loincMapper.resolve(parameter.getName());
        loinc.ifPresent(l -> code.addCoding(new Coding()
                .setSystem(FhirConstants.LOINC_SYSTEM)
                .setCode(l.code())
                .setDisplay(l.display())));
        observation.setCode(code);

        observation.setSubject(new Reference("Patient/" + analysis.getPatientId()));

        if (analysis.getCreatedAt() != null) {
            observation.setEffective(new DateTimeType(Date.from(analysis.getCreatedAt())));
        }

        if (parameter.getValue() != null) {
            Quantity quantity = new Quantity()
                    .setValue(parameter.getValue())
                    .setUnit(parameter.getUnit());
            // UCUM only where LOINC told us the expected unit. Declaring the lab's free-text unit
            // as UCUM would assert a conformance this has not checked.
            loinc.ifPresent(l -> quantity.setSystem("http://unitsofmeasure.org").setCode(l.unit()));
            observation.setValue(quantity);
        }

        interpretation(parameter.getFlag()).ifPresent(observation::addInterpretation);
        referenceRange(parameter).ifPresent(observation::addReferenceRange);

        return observation;
    }

    /**
     * A result is only {@code final} once the analysis that produced it completed. Publishing a
     * partial extraction as final is how a provisional number ends up treated as a reported one.
     */
    private Observation.ObservationStatus observationStatus(AnalysisRequest analysis) {
        return switch (analysis.getStatus()) {
            case COMPLETED -> Observation.ObservationStatus.FINAL;
            case FAILED -> Observation.ObservationStatus.ENTEREDINERROR;
            default -> Observation.ObservationStatus.PRELIMINARY;
        };
    }

    private Optional<CodeableConcept> interpretation(String flag) {
        if (flag == null || flag.isBlank()) {
            return Optional.empty();
        }

        String code = switch (flag.toUpperCase()) {
            case "HIGH" -> "H";
            case "LOW" -> "L";
            case "CRITICAL_HIGH" -> "HH";
            case "CRITICAL_LOW" -> "LL";
            case "NORMAL" -> "N";
            default -> null;
        };
        if (code == null) {
            return Optional.empty();
        }

        return Optional.of(new CodeableConcept().addCoding(new Coding()
                .setSystem(FhirConstants.INTERPRETATION_SYSTEM)
                .setCode(code)
                .setDisplay(flag)));
    }

    /**
     * Parses the reference range into low/high where its shape is unambiguous, and falls back to
     * {@code text} otherwise. A range that cannot be parsed is still worth carrying — a clinician
     * reading the resource needs it even when a machine cannot compare against it.
     */
    private Optional<Observation.ObservationReferenceRangeComponent> referenceRange(
            BloodReportResultDto.Parameter parameter) {

        String raw = parameter.getReferenceRange();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        var range = new Observation.ObservationReferenceRangeComponent().setText(raw.trim());
        String unit = parameter.getUnit();

        Matcher both = RANGE.matcher(raw);
        if (both.matches()) {
            range.setLow(simpleQuantity(Double.parseDouble(both.group(1)), unit));
            range.setHigh(simpleQuantity(Double.parseDouble(both.group(2)), unit));
            return Optional.of(range);
        }

        Matcher upper = UPPER_ONLY.matcher(raw);
        if (upper.matches()) {
            range.setHigh(simpleQuantity(Double.parseDouble(upper.group(1)), unit));
            return Optional.of(range);
        }

        Matcher lower = LOWER_ONLY.matcher(raw);
        if (lower.matches()) {
            range.setLow(simpleQuantity(Double.parseDouble(lower.group(1)), unit));
            return Optional.of(range);
        }

        return Optional.of(range);
    }

    private Quantity simpleQuantity(double value, String unit) {
        Quantity quantity = new Quantity().setValue(value);
        if (unit != null && !unit.isBlank()) {
            quantity.setUnit(unit);
        }
        return quantity;
    }

    /** References to every Observation produced for one report, for DiagnosticReport.result. */
    public List<Reference> referencesFor(AnalysisRequest analysis, int parameterCount) {
        return java.util.stream.IntStream.range(0, parameterCount)
                .mapToObj(i -> new Reference("Observation/" + analysis.getId() + "-obs-" + i))
                .toList();
    }
}
