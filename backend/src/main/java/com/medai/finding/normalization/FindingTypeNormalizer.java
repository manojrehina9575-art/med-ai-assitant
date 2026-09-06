package com.medai.finding.normalization;

import com.medai.finding.model.FindingType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class FindingTypeNormalizer {

    private static final Map<FindingType, Pattern> PATTERNS = buildPatterns();

    public List<FindingTypeMatch> findMatches(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<FindingTypeMatch> matches = new ArrayList<>();
        for (Map.Entry<FindingType, Pattern> entry : PATTERNS.entrySet()) {
            var matcher = entry.getValue().matcher(text);
            if (matcher.find()) {
                matches.add(new FindingTypeMatch(entry.getKey(), matcher.group(), matcher.start(), matcher.end()));
            }
        }
        matches.sort(Comparator.comparingInt(FindingTypeMatch::start));
        return matches;
    }

    private static Map<FindingType, Pattern> buildPatterns() {
        Map<FindingType, Pattern> patterns = new EnumMap<>(FindingType.class);
        patterns.put(FindingType.ANEURYSM, pattern("aneurysm", "aneurysms"));
        patterns.put(FindingType.FRACTURE, pattern("fracture", "fractures", "fractured"));
        patterns.put(FindingType.LESION, pattern("lesion", "lesions"));
        patterns.put(FindingType.NODULE, pattern("nodule", "nodules"));
        patterns.put(FindingType.EFFUSION, pattern("effusion", "effusions"));
        patterns.put(FindingType.MASS, pattern("mass", "masses"));
        patterns.put(FindingType.DISLOCATION, pattern("dislocation", "dislocations", "dislocated"));
        return Map.copyOf(patterns);
    }

    private static Pattern pattern(String... aliases) {
        String pattern = String.join("|", java.util.Arrays.stream(aliases)
                .map(Pattern::quote)
                .toList());
        return Pattern.compile("(?i)(?<![A-Za-z0-9])(?:" + pattern + ")(?![A-Za-z0-9])");
    }

    public record FindingTypeMatch(FindingType type, String text, int start, int end) {
    }
}
