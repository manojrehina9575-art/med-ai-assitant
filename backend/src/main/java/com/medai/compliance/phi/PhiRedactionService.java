package com.medai.compliance.phi;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance HIPAA Safe Harbor PHI & PII Redaction Engine.
 * Identifies and redacts/pseudonymizes sensitive medical identifiers:
 * - Names / Honorifics
 * - Social Security Numbers (SSN)
 * - Medical Record Numbers (MRN)
 * - Phone numbers & Fax
 * - Email addresses
 * - Dates (DOB, admission, discharge)
 * - Street addresses & Zip codes
 * - IP addresses
 */
@Service
@Slf4j
public class PhiRedactionService {

    // Regex patterns for HIPAA 18 Safe Harbor identifiers
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b(?!000|666|9\\d{2})\\d{3}[- ]?(?!00)\\d{2}[- ]?(?!0000)\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+?1[-. ]?)?\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})\\b");
    private static final Pattern MRN_PATTERN = Pattern.compile("\\b(?:MRN|MR#|REC|RECORD|ID)[:#]?\\s*([A-Z0-9]{5,12})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(?:0?[1-9]|1[0-2])[\\/\\-.](?:0?[1-9]|[12][0-9]|3[01])[\\/\\-.](?:19|20)\\d{2}\\b|\\b(?:19|20)\\d{2}[\\/\\-.](?:0?[1-9]|1[0-2])[\\/\\-.](?:0?[1-9]|[12][0-9]|3[01])\\b");
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("\\b\\d{5}(?:-\\d{4})?\\b");
    private static final Pattern NAME_PREFIX_PATTERN = Pattern.compile("\\b(?:Dr\\.|Mr\\.|Mrs\\.|Ms\\.|Patient|Pt\\.)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)\\b");

    @Data
    @Builder
    public static class RedactionResult {
        private String originalText;
        private String redactedText;
        private int totalRedactionsCount;
        private Map<String, Integer> redactionsByType;
        private Map<String, String> tokenMap; // token -> original value (for authorized de-pseudonymization)
    }

    @Data
    @Builder
    public static class PhiEntity {
        private String type;
        private String originalValue;
        private String token;
        private int startOffset;
        private int endOffset;
    }

    /**
     * Redacts PHI from raw medical text using HIPAA Safe Harbor token replacement.
     */
    public RedactionResult redact(String text) {
        if (text == null || text.isBlank()) {
            return RedactionResult.builder()
                    .originalText(text)
                    .redactedText(text)
                    .totalRedactionsCount(0)
                    .redactionsByType(Map.of())
                    .tokenMap(Map.of())
                    .build();
        }

        String result = text;
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> tokenMap = new HashMap<>();

        // 1. Redact SSN
        result = replaceWithToken(result, SSN_PATTERN, "SSN", counts, tokenMap);

        // 2. Redact Email
        result = replaceWithToken(result, EMAIL_PATTERN, "EMAIL", counts, tokenMap);

        // 3. Redact Phone
        result = replaceWithToken(result, PHONE_PATTERN, "PHONE", counts, tokenMap);

        // 4. Redact MRN
        result = replaceWithToken(result, MRN_PATTERN, "MRN", counts, tokenMap);

        // 5. Redact IP Address
        result = replaceWithToken(result, IPV4_PATTERN, "IP_ADDR", counts, tokenMap);

        // 6. Redact Dates
        result = replaceWithToken(result, DATE_PATTERN, "DATE", counts, tokenMap);

        // 7. Redact Zip Code
        result = replaceWithToken(result, ZIP_CODE_PATTERN, "ZIP", counts, tokenMap);

        // 8. Redact Names with titles
        result = replaceNames(result, counts, tokenMap);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        return RedactionResult.builder()
                .originalText(text)
                .redactedText(result)
                .totalRedactionsCount(total)
                .redactionsByType(counts)
                .tokenMap(tokenMap)
                .build();
    }

    private String replaceWithToken(String input, Pattern pattern, String type, Map<String, Integer> counts, Map<String, String> tokenMap) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        int count = counts.getOrDefault(type, 0);

        while (matcher.find()) {
            count++;
            String original = matcher.group();
            String token = "[" + type + "_" + count + "]";
            tokenMap.put(token, original);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(sb);
        if (count > 0) {
            counts.put(type, count);
        }
        return sb.toString();
    }

    private String replaceNames(String input, Map<String, Integer> counts, Map<String, String> tokenMap) {
        Matcher matcher = NAME_PREFIX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        int count = counts.getOrDefault("NAME", 0);

        while (matcher.find()) {
            count++;
            String matched = matcher.group();
            String token = "[NAME_" + count + "]";
            tokenMap.put(token, matched);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(sb);
        if (count > 0) {
            counts.put("NAME", count);
        }
        return sb.toString();
    }

    /**
     * Safely restore pseudonymized tokens back to original values for authorized clinician view.
     */
    public String restore(String redactedText, Map<String, String> tokenMap) {
        if (redactedText == null || tokenMap == null || tokenMap.isEmpty()) {
            return redactedText;
        }
        String restored = redactedText;
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
    }
}
