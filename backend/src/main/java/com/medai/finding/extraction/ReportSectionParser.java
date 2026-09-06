package com.medai.finding.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.finding.model.FindingSourceSection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ReportSectionParser {

    private static final Pattern HEADED_SECTION = Pattern.compile(
            "(?is)\\b(FINDINGS?|COMPARISON|IMPRESSION)\\b\\s*:?(.*?)(?=\\b(?:FINDINGS?|COMPARISON|IMPRESSION|RECOMMENDATIONS?|ICD-?10)\\b\\s*:?|\\z)");

    private final ObjectMapper objectMapper;

    public List<ReportSectionText> parse(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }

        Optional<List<ReportSectionText>> jsonSections = parseJsonReport(source);
        if (jsonSections.isPresent()) {
            return jsonSections.get();
        }

        List<ReportSectionText> headedSections = parseHeadedReport(source);
        if (!headedSections.isEmpty()) {
            return headedSections;
        }

        return List.of(new ReportSectionText(FindingSourceSection.UNKNOWN, source));
    }

    private Optional<List<ReportSectionText>> parseJsonReport(String source) {
        try {
            JsonNode root = objectMapper.readTree(source);
            if (!root.isObject()) {
                return Optional.empty();
            }

            List<ReportSectionText> sections = new ArrayList<>();
            readFindings(root.get("findings")).stream()
                    .map(text -> new ReportSectionText(FindingSourceSection.FINDINGS, text))
                    .forEach(sections::add);
            firstText(root, "comparison", "comparisons")
                    .map(text -> new ReportSectionText(FindingSourceSection.COMPARISON, text))
                    .ifPresent(sections::add);
            firstText(root, "impression", "overall_impression", "interpretation")
                    .map(text -> new ReportSectionText(FindingSourceSection.IMPRESSION, text))
                    .ifPresent(sections::add);
            return Optional.of(sections);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private List<String> readFindings(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return hasText(node.asText()) ? List.of(node.asText()) : List.of();
        }
        if (!node.isArray()) {
            return List.of();
        }

        List<String> findings = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && hasText(item.asText())) {
                findings.add(item.asText());
            } else if (item.isObject()) {
                String region = firstText(item, "region", "anatomy", "structure").orElse("");
                String description = firstText(item, "description", "text", "finding").orElse("");
                if (hasText(region) && hasText(description)) {
                    findings.add(region + ": " + description);
                } else if (hasText(description)) {
                    findings.add(description);
                }
            }
        }
        return findings;
    }

    private List<ReportSectionText> parseHeadedReport(String source) {
        var matcher = HEADED_SECTION.matcher(source);
        List<ReportSectionText> sections = new ArrayList<>();
        while (matcher.find()) {
            String text = matcher.group(2);
            if (hasText(text)) {
                sections.add(new ReportSectionText(sourceSection(matcher.group(1)), text));
            }
        }
        return sections;
    }

    private FindingSourceSection sourceSection(String heading) {
        String normalized = heading.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("FINDING")) {
            return FindingSourceSection.FINDINGS;
        }
        if (normalized.equals("COMPARISON")) {
            return FindingSourceSection.COMPARISON;
        }
        if (normalized.equals("IMPRESSION")) {
            return FindingSourceSection.IMPRESSION;
        }
        return FindingSourceSection.UNKNOWN;
    }

    private Optional<String> firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && hasText(value.asText())) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
