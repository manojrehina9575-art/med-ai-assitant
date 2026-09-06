package com.medai.longitudinal.comparison;

import com.medai.finding.model.StructuredFinding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Component
public class MeasurementComparator {

    public Optional<MeasurementComparison> compare(StructuredFinding prior, StructuredFinding current) {
        Optional<BigDecimal> priorMillimeters = toMillimeters(prior);
        Optional<BigDecimal> currentMillimeters = toMillimeters(current);
        if (priorMillimeters.isEmpty() || currentMillimeters.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal priorValue = priorMillimeters.get();
        BigDecimal currentValue = currentMillimeters.get();
        return Optional.of(new MeasurementComparison(
                priorValue,
                currentValue,
                currentValue.subtract(priorValue).stripTrailingZeros(),
                currentValue.compareTo(priorValue)));
    }

    public Optional<BigDecimal> toMillimeters(StructuredFinding finding) {
        if (finding == null || finding.measurement() == null || finding.unit() == null) {
            return Optional.empty();
        }

        BigDecimal value = BigDecimal.valueOf(finding.measurement());
        String unit = finding.unit().strip().toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "mm" -> Optional.of(value.stripTrailingZeros());
            case "cm" -> Optional.of(value.multiply(BigDecimal.TEN).stripTrailingZeros());
            default -> Optional.empty();
        };
    }

    public record MeasurementComparison(
            BigDecimal priorMillimeters,
            BigDecimal currentMillimeters,
            BigDecimal deltaMillimeters,
            int direction
    ) {
    }
}
