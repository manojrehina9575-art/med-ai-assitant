package com.medai.qa.rules;

import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.normalization.LateralityNormalizer;
import com.medai.qa.model.LateralitySide;
import com.medai.qa.model.QaIssue;
import com.medai.qa.model.QaIssueType;
import com.medai.qa.model.QaSeverity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class LateralityRule {

    private static final String DETECTOR = "LateralityRule";
    private static final String DETECTOR_VERSION = "1.0.0";

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+|\\R+|;");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "into",
            "is", "it", "no", "not", "of", "on", "or", "the", "there", "to", "was", "were",
            "with", "without", "within", "identified", "involving", "involves", "seen", "small",
            "mild", "moderate", "severe", "large", "acute", "chronic", "new", "old"
    );

    private static final Set<String> REGION_TERMS = Set.of(
            "proximal", "distal", "mid", "upper", "lower", "medial", "lateral", "anterior",
            "posterior", "neck"
    );

    private static final Set<String> ANATOMY_TERMS = Set.of(
            "ankle", "abdomen", "brain", "chest", "clavicle", "elbow", "femur", "fibula",
            "hip", "humerus", "kidney", "knee", "lung", "pleura", "radius", "shoulder",
            "spine", "tibia", "ulna", "wrist"
    );

    private static final Set<String> FINDING_TERMS = Set.of(
            "aneurysm", "calcification", "consolidation", "cyst", "dislocation", "edema",
            "effusion", "fracture", "hemorrhage", "infarct", "lesion", "mass", "nodule",
            "opacity", "pneumonia", "pneumothorax", "stenosis", "swelling", "tear"
    );

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("femoral", "femur"),
            Map.entry("humeral", "humerus"),
            Map.entry("renal", "kidney"),
            Map.entry("kidneys", "kidney"),
            Map.entry("lungs", "lung"),
            Map.entry("pulmonary", "lung"),
            Map.entry("pleural", "pleura"),
            Map.entry("effusions", "effusion"),
            Map.entry("fractures", "fracture"),
            Map.entry("fractured", "fracture"),
            Map.entry("lesions", "lesion"),
            Map.entry("opacities", "opacity"),
            Map.entry("nodules", "nodule"),
            Map.entry("masses", "mass"),
            Map.entry("cysts", "cyst")
    );

    public List<QaIssue> evaluate(String findings, String impression) {
        return evaluate(splitStatements(findings), impression);
    }

    public List<QaIssue> evaluate(List<String> findings, String impression) {
        List<Statement> findingStatements = statements(findings);
        List<Statement> impressionStatements = statements(splitStatements(impression));
        List<QaIssue> issues = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (Statement finding : findingStatements) {
            for (Statement impressionStatement : impressionStatements) {
                if (finding.side() == impressionStatement.side()) {
                    continue;
                }
                Optional<ConceptMatch> match = matchingConcept(finding, impressionStatement);
                if (match.isEmpty()) {
                    continue;
                }

                String pairKey = finding.normalizedText() + "|" + impressionStatement.normalizedText();
                if (!seenPairs.add(pairKey)) {
                    continue;
                }

                issues.add(toIssue(issues.size() + 1, finding, impressionStatement, match.get()));
            }
        }

        return issues;
    }

    private QaIssue toIssue(int index, Statement finding, Statement impression, ConceptMatch match) {
        String anatomy = match.anatomyTerms().stream().sorted().findFirst().orElse("anatomical structure");
        String region = finding.regions().stream()
                .filter(impression.regions()::contains)
                .findFirst()
                .or(() -> finding.regions().stream().findFirst())
                .map(String::toUpperCase)
                .orElse(null);

        String message = "Potential laterality conflict. Clinician review required: Findings reference "
                + finding.side() + " " + anatomy
                + " while Impression references " + impression.side() + " " + anatomy + ".";

        return new QaIssue(
                "laterality-conflict-" + index,
                QaIssueType.LATERALITY_CONFLICT,
                QaSeverity.HIGH,
                message,
                finding.text(),
                impression.text(),
                "FINDINGS",
                "IMPRESSION",
                finding.side(),
                impression.side(),
                anatomy.toUpperCase(Locale.ROOT),
                region,
                0.90d,
                DETECTOR,
                DETECTOR_VERSION);
    }

    private List<Statement> statements(List<String> rawStatements) {
        return rawStatements.stream()
                .flatMap(text -> splitStatements(text).stream())
                .map(String::strip)
                .filter(text -> !text.isBlank())
                .map(this::toStatement)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Statement> toStatement(String text) {
        Optional<LateralitySide> side = lateralityOf(text);
        if (side.isEmpty()) {
            return Optional.empty();
        }

        Set<String> terms = TOKEN.matcher(text).results()
                .map(MatchResult::group)
                .map(this::canonicalToken)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> anatomy = terms.stream()
                .filter(ANATOMY_TERMS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> findings = terms.stream()
                .filter(FINDING_TERMS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> regions = terms.stream()
                .filter(REGION_TERMS::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return Optional.of(new Statement(text, normalizeForKey(text), side.get(), anatomy, findings, regions));
    }

    private Optional<ConceptMatch> matchingConcept(Statement finding, Statement impression) {
        Set<String> sharedAnatomy = intersection(finding.anatomyTerms(), impression.anatomyTerms());
        if (sharedAnatomy.isEmpty()) {
            return Optional.empty();
        }

        Set<String> sharedFindings = intersection(finding.findingTerms(), impression.findingTerms());
        if (sharedFindings.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ConceptMatch(sharedAnatomy, sharedFindings));
    }

    private Optional<LateralitySide> lateralityOf(String text) {
        return LateralityNormalizer.unilateralSide(text)
                .map(side -> side == AnatomicalSide.RIGHT ? LateralitySide.RIGHT : LateralitySide.LEFT);
    }

    private Optional<String> canonicalToken(String raw) {
        String token = raw.toLowerCase(Locale.ROOT);

        if (isLateralityToken(token) || "bilateral".equals(token) || "bilaterally".equals(token)) {
            return Optional.empty();
        }
        if (STOP_WORDS.contains(token)) {
            return Optional.empty();
        }

        String canonical = SYNONYMS.getOrDefault(token, token);
        if (canonical.endsWith("s") && canonical.length() > 4 && !"humerus".equals(canonical)) {
            canonical = canonical.substring(0, canonical.length() - 1);
        }
        if (STOP_WORDS.contains(canonical)) {
            return Optional.empty();
        }
        return Optional.of(canonical);
    }

    private boolean isLateralityToken(String token) {
        return LateralityNormalizer.isLateralityToken(token);
    }

    private List<String> splitStatements(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return SENTENCE_SPLIT.splitAsStream(text)
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private String normalizeForKey(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private record Statement(
            String text,
            String normalizedText,
            LateralitySide side,
            Set<String> anatomyTerms,
            Set<String> findingTerms,
            Set<String> regions
    ) {
    }

    private record ConceptMatch(Set<String> anatomyTerms, Set<String> findingTerms) {
    }
}
