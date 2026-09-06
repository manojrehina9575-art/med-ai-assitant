package com.medai.finding.normalization;

import com.medai.finding.model.AnatomicalStructure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class AnatomyNormalizer {

    private static final List<Alias> ALIASES = List.of(
            alias(AnatomicalStructure.HUMERUS, "humerus", "humeral"),
            alias(AnatomicalStructure.FEMUR, "femur", "femoral"),
            alias(AnatomicalStructure.BRAIN, "brain", "cerebral", "intracranial"),
            alias(AnatomicalStructure.KIDNEY, "kidney", "kidneys", "renal"),
            alias(AnatomicalStructure.LUNG, "lung", "lungs", "pulmonary"),
            alias(AnatomicalStructure.PLEURA, "pleura", "pleural"),
            alias(AnatomicalStructure.SHOULDER, "shoulder", "glenohumeral"),
            alias(AnatomicalStructure.ANKLE, "ankle"),
            alias(AnatomicalStructure.KNEE, "knee")
    );

    public Optional<AnatomyMatch> findBest(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        List<AnatomyMatch> matches = new ArrayList<>();
        for (Alias alias : ALIASES) {
            var matcher = alias.pattern().matcher(text);
            if (matcher.find()) {
                matches.add(new AnatomyMatch(alias.structure(), matcher.group(), matcher.start(), matcher.end()));
            }
        }

        return matches.stream()
                .min(Comparator
                        .comparingInt(AnatomyMatch::start)
                        .thenComparing((a, b) -> Integer.compare(b.text().length(), a.text().length())));
    }

    private static Alias alias(AnatomicalStructure structure, String... aliases) {
        String pattern = String.join("|", java.util.Arrays.stream(aliases)
                .map(Pattern::quote)
                .toList());
        return new Alias(
                structure,
                Pattern.compile("(?i)(?<![A-Za-z0-9])(?:" + pattern + ")(?![A-Za-z0-9])"));
    }

    private record Alias(AnatomicalStructure structure, Pattern pattern) {
    }

    public record AnatomyMatch(AnatomicalStructure structure, String text, int start, int end) {
    }
}
