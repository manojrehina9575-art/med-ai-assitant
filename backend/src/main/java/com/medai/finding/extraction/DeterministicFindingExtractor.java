package com.medai.finding.extraction;

import com.medai.finding.model.*;
import com.medai.finding.normalization.AnatomyNormalizer;
import com.medai.finding.normalization.FindingTypeNormalizer;
import com.medai.finding.normalization.LateralityNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DeterministicFindingExtractor implements FindingExtractor {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+|\\R+|;");
    private static final Pattern NEGATION = Pattern.compile(
            "(?i)\\b(?:no|without|negative\\s+for|no\\s+evidence\\s+of|not\\s+identified|not\\s+seen)\\b");
    private static final Pattern NO_CHANGE = Pattern.compile("(?i)\\bno\\s+(?:significant\\s+)?change\\b");
    private static final Pattern ABSENT = Pattern.compile("(?i)\\babsent\\b");
    private static final Pattern POSSIBLE = Pattern.compile(
            "(?i)\\b(?:possible|possibly|may\\s+represent|could\\s+represent|questionable)\\b");
    private static final Pattern SUSPECTED = Pattern.compile(
            "(?i)\\b(?:suspected|suspicious\\s+for|concerning\\s+for)\\b");
    private static final Pattern MEASUREMENT = Pattern.compile("(?i)(?<![0-9.])(\\d+(?:\\.\\d+)?)\\s*(mm|cm)(?![A-Za-z])");

    private final FindingTypeNormalizer findingTypeNormalizer;
    private final AnatomyNormalizer anatomyNormalizer;

    @Override
    public List<StructuredFinding> extract(FindingSourceSection sourceSection, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        FindingSourceSection section = sourceSection == null ? FindingSourceSection.UNKNOWN : sourceSection;
        List<StructuredFinding> findings = new ArrayList<>();
        for (Segment segment : splitSegments(text)) {
            List<FindingTypeNormalizer.FindingTypeMatch> typeMatches =
                    findingTypeNormalizer.findMatches(segment.text());
            if (typeMatches.isEmpty()) {
                continue;
            }

            Optional<AnatomyNormalizer.AnatomyMatch> anatomyMatch = anatomyNormalizer.findBest(segment.text());
            AnatomicalSide side = LateralityNormalizer.detect(segment.text());
            AnatomicalRegion region = regionOf(segment.text());
            FindingCertainty certainty = certaintyOf(segment.text());
            Measurement measurement = measurementOf(segment.text());

            for (FindingTypeNormalizer.FindingTypeMatch typeMatch : typeMatches) {
                FindingStatus status = statusOf(segment.text(), typeMatch.start());
                findings.add(new StructuredFinding(
                        localKey(section, findings.size() + 1, typeMatch.type()),
                        typeMatch.type(),
                        anatomyMatch.map(AnatomyNormalizer.AnatomyMatch::structure).orElse(null),
                        anatomyMatch.map(AnatomyNormalizer.AnatomyMatch::text).orElse(null),
                        side,
                        region,
                        status,
                        certainty,
                        measurement.value(),
                        measurement.unit(),
                        section,
                        segment.text(),
                        segment.start(),
                        segment.end(),
                        normalizedTerms(typeMatch.type(), anatomyMatch, side, region, status, certainty)));
            }
        }
        return findings;
    }

    private List<Segment> splitSegments(String text) {
        List<Segment> segments = new ArrayList<>();
        var matcher = SENTENCE_SPLIT.matcher(text);
        int start = 0;
        while (matcher.find()) {
            addSegment(segments, text, start, matcher.start());
            start = matcher.end();
        }
        addSegment(segments, text, start, text.length());
        return segments;
    }

    private void addSegment(List<Segment> segments, String text, int rawStart, int rawEnd) {
        int start = rawStart;
        int end = rawEnd;
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start < end) {
            segments.add(new Segment(text.substring(start, end), start, end));
        }
    }

    private FindingStatus statusOf(String text, int findingStart) {
        if (ABSENT.matcher(text).find()) {
            return FindingStatus.ABSENT;
        }
        if (NO_CHANGE.matcher(text).find()) {
            return FindingStatus.PRESENT;
        }

        var matcher = NEGATION.matcher(text);
        while (matcher.find()) {
            if (matcher.start() <= findingStart && findingStart - matcher.start() <= 80) {
                return FindingStatus.ABSENT;
            }
        }
        return FindingStatus.PRESENT;
    }

    private FindingCertainty certaintyOf(String text) {
        if (POSSIBLE.matcher(text).find()) {
            return FindingCertainty.POSSIBLE;
        }
        if (SUSPECTED.matcher(text).find()) {
            return FindingCertainty.SUSPECTED;
        }
        return FindingCertainty.ASSERTED;
    }

    private AnatomicalRegion regionOf(String text) {
        String lower = " " + text.toLowerCase(Locale.ROOT) + " ";
        if (containsTerm(lower, "proximal")) return AnatomicalRegion.PROXIMAL;
        if (containsTerm(lower, "distal")) return AnatomicalRegion.DISTAL;
        if (containsTerm(lower, "apical")) return AnatomicalRegion.APICAL;
        if (containsTerm(lower, "basal")) return AnatomicalRegion.BASAL;
        if (containsTerm(lower, "upper")) return AnatomicalRegion.UPPER;
        if (containsTerm(lower, "lower")) return AnatomicalRegion.LOWER;
        if (containsTerm(lower, "mid") || Pattern.compile("(?i)(?<![A-Za-z0-9])mid[- ]shaft(?![A-Za-z0-9])").matcher(text).find()) {
            return AnatomicalRegion.MID;
        }
        return AnatomicalRegion.UNSPECIFIED;
    }

    private boolean containsTerm(String paddedLowerText, String term) {
        return paddedLowerText.matches("(?s).*[^A-Za-z0-9]" + Pattern.quote(term) + "[^A-Za-z0-9].*");
    }

    private Measurement measurementOf(String text) {
        var matcher = MEASUREMENT.matcher(text);
        if (!matcher.find()) {
            return new Measurement(null, null);
        }
        return new Measurement(Double.valueOf(matcher.group(1)), matcher.group(2).toLowerCase(Locale.ROOT));
    }

    private Set<String> normalizedTerms(
            FindingType type,
            Optional<AnatomyNormalizer.AnatomyMatch> anatomyMatch,
            AnatomicalSide side,
            AnatomicalRegion region,
            FindingStatus status,
            FindingCertainty certainty
    ) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(type.name());
        anatomyMatch.map(AnatomyNormalizer.AnatomyMatch::structure).map(Enum::name).ifPresent(terms::add);
        if (side != AnatomicalSide.UNSPECIFIED) {
            terms.add(side.name());
        }
        if (region != AnatomicalRegion.UNSPECIFIED) {
            terms.add(region.name());
        }
        terms.add(status.name());
        terms.add(certainty.name());
        return terms;
    }

    private String localKey(FindingSourceSection sourceSection, int index, FindingType type) {
        return sourceSection.name().toLowerCase(Locale.ROOT)
                + "-" + index
                + "-" + type.name().toLowerCase(Locale.ROOT);
    }

    private record Segment(String text, int start, int end) {
    }

    private record Measurement(Double value, String unit) {
    }
}
