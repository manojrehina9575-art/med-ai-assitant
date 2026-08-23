package com.medai.clinical.safety;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a proposed prescription against the patient's documented allergies, the other drugs on
 * the same prescription, and accepted daily dose ceilings.
 *
 * <p>Replaces {@code medicationName.contains(allergyText)}, which was wrong in both directions:
 * it missed every class-level allergy (penicillin documented, amoxicillin prescribed) and every
 * brand/generic mismatch (Bactrim documented, sulfamethoxazole-trimethoprim prescribed), while
 * matching substrings that mean nothing ("ace" inside "acetaminophen").
 *
 * <p>Findings are advisory in the sense that a prescriber may override them, but never silent:
 * {@link Assessment#requiresAcknowledgement()} is what the caller must respect, and an override
 * has to name the person making it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrugSafetyService {

    private final DrugKnowledgeBase knowledgeBase;

    /**
     * One medication as written on the prescription.
     */
    public record ProposedMedication(String name, String dosage, String frequency, String duration) {
    }

    /**
     * The verdict on a whole prescription.
     *
     * @param findings              everything found, most severe first
     * @param normalisedIngredients what each medication resolved to, for the record
     * @param unrecognised          medications the knowledge base does not know, which therefore
     *                              received no checking at all
     */
    public record Assessment(
            List<DrugSafetyFinding> findings,
            List<String> normalisedIngredients,
            List<String> unrecognised
    ) {
        public boolean requiresAcknowledgement() {
            return findings.stream().anyMatch(DrugSafetyFinding::blocking);
        }

        public boolean isClear() {
            return findings.isEmpty();
        }

        /** Stable codes a prescriber must acknowledge by name to override. */
        public Set<String> blockingCodes() {
            return findings.stream()
                    .filter(DrugSafetyFinding::blocking)
                    .map(DrugSafetyFinding::code)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    public Assessment assess(List<ProposedMedication> medications, List<String> documentedAllergies) {
        List<DrugSafetyFinding> findings = new ArrayList<>();
        List<String> ingredients = new ArrayList<>();
        List<String> unrecognised = new ArrayList<>();

        for (ProposedMedication med : medications) {
            String ingredient = knowledgeBase.normalise(med.name());
            ingredients.add(ingredient);
            if (!knowledgeBase.isKnown(ingredient)) {
                unrecognised.add(med.name());
            }
        }

        findings.addAll(checkAllergies(medications, ingredients, documentedAllergies));
        findings.addAll(checkInteractions(ingredients));
        findings.addAll(checkDuplicateTherapy(ingredients));
        findings.addAll(checkDoseCeilings(medications, ingredients));
        findings.addAll(checkRenalAdjustment(ingredients));

        if (!unrecognised.isEmpty()) {
            findings.add(DrugSafetyFinding.moderate(
                    "UNRECOGNISED_MEDICATION",
                    "Not present in the drug knowledge base, so no allergy, interaction or dose check "
                    + "was performed for: " + String.join(", ", unrecognised)
                    + ". Verify manually.",
                    unrecognised.toArray(String[]::new)));
        }

        findings.sort(Comparator.comparing(DrugSafetyFinding::severity));

        return new Assessment(List.copyOf(findings), List.copyOf(ingredients), List.copyOf(unrecognised));
    }

    // ── Allergies ────────────────────────────────────────────────────────────

    private List<DrugSafetyFinding> checkAllergies(List<ProposedMedication> medications,
                                                   List<String> ingredients,
                                                   List<String> documentedAllergies) {
        if (documentedAllergies == null || documentedAllergies.isEmpty()) {
            return List.of();
        }

        List<DrugSafetyFinding> findings = new ArrayList<>();

        // Normalise the allergy list the same way as the prescription, so "Bactrim" on one side
        // and "sulfamethoxazole-trimethoprim" on the other are the same fact.
        Map<String, String> allergyIngredients = new LinkedHashMap<>();
        for (String allergy : documentedAllergies) {
            if (allergy == null || allergy.isBlank()) {
                continue;
            }
            allergyIngredients.put(knowledgeBase.normalise(allergy), allergy.trim());
        }

        for (int i = 0; i < ingredients.size(); i++) {
            String ingredient = ingredients.get(i);
            String asWritten = medications.get(i).name();
            Set<String> ingredientClasses = knowledgeBase.classesOf(ingredient);

            for (Map.Entry<String, String> entry : allergyIngredients.entrySet()) {
                String allergyIngredient = entry.getKey();
                String allergyAsDocumented = entry.getValue();

                // Same ingredient.
                if (allergyIngredient.equals(ingredient)) {
                    findings.add(DrugSafetyFinding.contraindicated(
                            "ALLERGY_DIRECT",
                            "Patient has a documented allergy to " + allergyAsDocumented
                            + ", which is the same ingredient as the prescribed " + asWritten + ".",
                            ingredient));
                    continue;
                }

                Set<String> allergyClasses = knowledgeBase.classesOf(allergyIngredient);

                // The allergy is recorded as a class ("penicillins", "sulfa", "NSAIDs") rather
                // than a specific drug — the common case in a real allergy list.
                if (ingredientClasses.contains(allergyIngredient)
                    || ingredientClasses.contains(singularise(allergyIngredient))) {
                    findings.add(DrugSafetyFinding.contraindicated(
                            "ALLERGY_CLASS",
                            "Patient has a documented allergy to " + allergyAsDocumented + ". "
                            + asWritten + " belongs to that class.",
                            ingredient));
                    continue;
                }

                // Both are known drugs sharing a class.
                Set<String> shared = new HashSet<>(allergyClasses);
                shared.retainAll(ingredientClasses);
                if (!shared.isEmpty()) {
                    findings.add(DrugSafetyFinding.contraindicated(
                            "ALLERGY_SAME_CLASS",
                            "Patient has a documented allergy to " + allergyAsDocumented + ". "
                            + asWritten + " is in the same class (" + String.join(", ", shared) + ").",
                            ingredient, allergyIngredient));
                    continue;
                }

                // Structurally related classes — a warning, not an absolute bar.
                for (String allergyClass : allergyClasses) {
                    for (Map.Entry<String, String> cross : knowledgeBase.crossReactiveWith(allergyClass).entrySet()) {
                        if (ingredientClasses.contains(cross.getKey())) {
                            findings.add(DrugSafetyFinding.major(
                                    "ALLERGY_CROSS_REACTIVE",
                                    "Patient has a documented allergy to " + allergyAsDocumented
                                    + " (" + allergyClass + "). " + cross.getValue(),
                                    ingredient, allergyIngredient));
                        }
                    }
                }
            }
        }
        return findings;
    }

    /** "penicillins" and "sulfonamides" are how allergy lists are actually written. */
    private String singularise(String s) {
        return s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
    }

    // ── Interactions ─────────────────────────────────────────────────────────

    private List<DrugSafetyFinding> checkInteractions(List<String> ingredients) {
        List<DrugSafetyFinding> findings = new ArrayList<>();

        for (int i = 0; i < ingredients.size(); i++) {
            for (int j = i + 1; j < ingredients.size(); j++) {
                String a = ingredients.get(i);
                String b = ingredients.get(j);

                Set<String> tokensA = tokensFor(a);
                Set<String> tokensB = tokensFor(b);

                for (DrugKnowledgeBase.Interaction interaction : knowledgeBase.interactions()) {
                    boolean matches =
                            (tokensA.contains(interaction.a()) && tokensB.contains(interaction.b()))
                            || (tokensA.contains(interaction.b()) && tokensB.contains(interaction.a()));

                    if (matches) {
                        findings.add(new DrugSafetyFinding(
                                interaction.severity(),
                                "INTERACTION",
                                a + " + " + b + ": " + interaction.mechanism(),
                                List.of(a, b)));
                    }
                }
            }
        }
        return findings;
    }

    /** An ingredient matches an interaction rule by its own name or by any class it belongs to. */
    private Set<String> tokensFor(String ingredient) {
        Set<String> tokens = new HashSet<>(knowledgeBase.classesOf(ingredient));
        tokens.add(ingredient);
        return tokens;
    }

    // ── Duplicate therapy ────────────────────────────────────────────────────

    private List<DrugSafetyFinding> checkDuplicateTherapy(List<String> ingredients) {
        List<DrugSafetyFinding> findings = new ArrayList<>();

        Map<String, List<String>> byIngredient = new LinkedHashMap<>();
        for (String ingredient : ingredients) {
            byIngredient.computeIfAbsent(ingredient, k -> new ArrayList<>()).add(ingredient);
        }
        byIngredient.forEach((ingredient, occurrences) -> {
            if (occurrences.size() > 1) {
                findings.add(DrugSafetyFinding.major(
                        "DUPLICATE_INGREDIENT",
                        ingredient + " appears " + occurrences.size() + " times on this prescription. "
                        + "Doses are additive — confirm this is intended and within the daily maximum.",
                        ingredient));
            }
        });

        // Two different drugs from one therapeutic class is usually an error, and occasionally
        // deliberate, so it warns rather than blocks outright.
        Map<String, Set<String>> byClass = new LinkedHashMap<>();
        for (String ingredient : new LinkedHashSet<>(ingredients)) {
            for (String className : knowledgeBase.classesOf(ingredient)) {
                byClass.computeIfAbsent(className, k -> new LinkedHashSet<>()).add(ingredient);
            }
        }
        byClass.forEach((className, members) -> {
            if (members.size() > 1) {
                findings.add(DrugSafetyFinding.major(
                        "DUPLICATE_CLASS",
                        "Two drugs from the same class (" + className + "): "
                        + String.join(" and ", members)
                        + ". Additive effect and additive toxicity without additive benefit in most "
                        + "indications.",
                        members.toArray(String[]::new)));
            }
        });

        return findings;
    }

    // ── Dose ceilings ────────────────────────────────────────────────────────

    private static final Pattern DOSE_MG = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mg|g|mcg)");

    /**
     * Doses per day for the frequency abbreviations that appear on real prescriptions.
     * A frequency that cannot be read yields no finding rather than a guessed one.
     */
    private static final Map<String, Integer> DOSES_PER_DAY = Map.ofEntries(
            Map.entry("od", 1), Map.entry("qd", 1), Map.entry("daily", 1), Map.entry("once daily", 1),
            Map.entry("hs", 1), Map.entry("nocte", 1), Map.entry("q24h", 1),
            Map.entry("bid", 2), Map.entry("bd", 2), Map.entry("twice daily", 2), Map.entry("q12h", 2),
            Map.entry("tid", 3), Map.entry("tds", 3), Map.entry("three times daily", 3), Map.entry("q8h", 3),
            Map.entry("qid", 4), Map.entry("qds", 4), Map.entry("four times daily", 4), Map.entry("q6h", 4),
            Map.entry("q4h", 6)
    );

    private static final List<String> FREQUENCY_KEYS_LONGEST_FIRST = DOSES_PER_DAY.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private List<DrugSafetyFinding> checkDoseCeilings(List<ProposedMedication> medications,
                                                      List<String> ingredients) {
        List<DrugSafetyFinding> findings = new ArrayList<>();

        // Sum per ingredient: two lines of the same drug are one daily total.
        Map<String, Double> dailyTotals = new LinkedHashMap<>();

        for (int i = 0; i < medications.size(); i++) {
            ProposedMedication med = medications.get(i);
            String ingredient = ingredients.get(i);

            OptionalDouble singleDoseMg = parseDoseMg(med.dosage());
            OptionalInt perDay = parseFrequency(med.frequency());
            if (singleDoseMg.isEmpty() || perDay.isEmpty()) {
                continue;
            }

            dailyTotals.merge(ingredient, singleDoseMg.getAsDouble() * perDay.getAsInt(), Double::sum);
        }

        dailyTotals.forEach((ingredient, total) -> knowledgeBase.maxDailyMg(ingredient).ifPresent(max -> {
            if (total > max) {
                findings.add(DrugSafetyFinding.major(
                        "DOSE_EXCEEDS_MAXIMUM",
                        String.format(
                                "%s totals %.0f mg/day across this prescription, above the accepted adult "
                                + "maximum of %.0f mg/day. Confirm the indication and the patient's weight "
                                + "and renal function.",
                                ingredient, total, max),
                        ingredient));
            }
        }));

        return findings;
    }

    private OptionalDouble parseDoseMg(String dosage) {
        if (dosage == null) {
            return OptionalDouble.empty();
        }
        Matcher m = DOSE_MG.matcher(dosage.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return OptionalDouble.empty();
        }
        double value = Double.parseDouble(m.group(1));
        return OptionalDouble.of(switch (m.group(2)) {
            case "g" -> value * 1000;
            case "mcg" -> value / 1000;
            default -> value;
        });
    }

    private OptionalInt parseFrequency(String frequency) {
        if (frequency == null) {
            return OptionalInt.empty();
        }
        String f = frequency.toLowerCase(Locale.ROOT).trim();

        // "PRN" means as-needed, so there is no defined daily total to check against.
        if (f.contains("prn") || f.contains("as needed") || f.contains("as required")) {
            return OptionalInt.empty();
        }

        Integer exact = DOSES_PER_DAY.get(f);
        if (exact != null) {
            return OptionalInt.of(exact);
        }
        // Longest key first. Map.ofEntries randomises iteration order, so scanning it directly
        // would resolve "give twice daily" to either 2 or 1 depending on the run — matching the
        // most specific key removes both the ambiguity and the nondeterminism.
        for (String key : FREQUENCY_KEYS_LONGEST_FIRST) {
            if (f.contains(key)) {
                return OptionalInt.of(DOSES_PER_DAY.get(key));
            }
        }
        return OptionalInt.empty();
    }

    // ── Renal ────────────────────────────────────────────────────────────────

    private List<DrugSafetyFinding> checkRenalAdjustment(List<String> ingredients) {
        List<String> needsCheck = ingredients.stream()
                .distinct()
                .filter(knowledgeBase::requiresRenalAdjustment)
                .toList();

        if (needsCheck.isEmpty()) {
            return List.of();
        }

        // Deliberately not silent about the gap: the system holds no renal function for the
        // patient, so it cannot adjust and will not pretend the dose has been checked.
        return List.of(DrugSafetyFinding.moderate(
                "RENAL_ADJUSTMENT_UNVERIFIED",
                "Dose depends on renal function for: " + String.join(", ", needsCheck)
                + ". No eGFR is recorded for this patient, so no renal check was performed.",
                needsCheck.toArray(String[]::new)));
    }
}
