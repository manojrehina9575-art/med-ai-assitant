package com.medai.clinical.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * A curated drug knowledge base: brand-to-ingredient mapping, allergy cross-reactivity classes,
 * interaction pairs, and daily dose ceilings.
 *
 * <p><strong>This is a starter dataset, not a licensed drug compendium.</strong> It covers the
 * commonly prescribed ingredients and the interactions that account for most avoidable harm, and
 * it is deliberately structured so that swapping in First Databank, Medi-Span or an RxNav-backed
 * lookup means replacing this one class. Do not read its coverage as clinical completeness — an
 * ingredient it does not know produces no finding, which is why
 * {@link DrugSafetyService} reports unrecognised ingredients explicitly rather than passing them
 * silently.
 *
 * <p>What it replaces is worse in every direction: {@code medicationName.contains(allergyText)}.
 * That matched nothing for a penicillin allergy against amoxicillin, matched nothing for a
 * Bactrim allergy against sulfamethoxazole, and matched "ace" inside "acetaminophen".
 */
@Component
@Slf4j
public class DrugKnowledgeBase {

    /** Brand or synonym → normalised generic ingredient. */
    private static final Map<String, String> BRAND_TO_INGREDIENT = Map.ofEntries(
            Map.entry("tylenol", "acetaminophen"),
            Map.entry("paracetamol", "acetaminophen"),
            Map.entry("panadol", "acetaminophen"),
            Map.entry("crocin", "acetaminophen"),
            Map.entry("dolo", "acetaminophen"),
            Map.entry("advil", "ibuprofen"),
            Map.entry("motrin", "ibuprofen"),
            Map.entry("brufen", "ibuprofen"),
            Map.entry("aleve", "naproxen"),
            Map.entry("voltaren", "diclofenac"),
            Map.entry("toradol", "ketorolac"),
            Map.entry("aspirin", "acetylsalicylic acid"),
            Map.entry("ecosprin", "acetylsalicylic acid"),
            Map.entry("disprin", "acetylsalicylic acid"),
            Map.entry("augmentin", "amoxicillin-clavulanate"),
            Map.entry("amoxil", "amoxicillin"),
            Map.entry("zithromax", "azithromycin"),
            Map.entry("cipro", "ciprofloxacin"),
            Map.entry("levaquin", "levofloxacin"),
            Map.entry("bactrim", "sulfamethoxazole-trimethoprim"),
            Map.entry("septra", "sulfamethoxazole-trimethoprim"),
            Map.entry("cotrimoxazole", "sulfamethoxazole-trimethoprim"),
            Map.entry("septran", "sulfamethoxazole-trimethoprim"),
            Map.entry("rocephin", "ceftriaxone"),
            Map.entry("keflex", "cephalexin"),
            Map.entry("flagyl", "metronidazole"),
            Map.entry("coumadin", "warfarin"),
            Map.entry("eliquis", "apixaban"),
            Map.entry("xarelto", "rivaroxaban"),
            Map.entry("plavix", "clopidogrel"),
            Map.entry("lipitor", "atorvastatin"),
            Map.entry("zocor", "simvastatin"),
            Map.entry("crestor", "rosuvastatin"),
            Map.entry("glucophage", "metformin"),
            Map.entry("lasix", "furosemide"),
            Map.entry("aldactone", "spironolactone"),
            Map.entry("prinivil", "lisinopril"),
            Map.entry("zestril", "lisinopril"),
            Map.entry("cozaar", "losartan"),
            Map.entry("lanoxin", "digoxin"),
            Map.entry("cordarone", "amiodarone"),
            Map.entry("prozac", "fluoxetine"),
            Map.entry("zoloft", "sertraline"),
            Map.entry("lexapro", "escitalopram"),
            Map.entry("ultram", "tramadol"),
            Map.entry("oxycontin", "oxycodone"),
            Map.entry("percocet", "oxycodone"),
            Map.entry("vicodin", "hydrocodone"),
            Map.entry("dilaudid", "hydromorphone"),
            Map.entry("valium", "diazepam"),
            Map.entry("ativan", "lorazepam"),
            Map.entry("xanax", "alprazolam"),
            Map.entry("prilosec", "omeprazole"),
            Map.entry("zofran", "ondansetron"),
            Map.entry("neurontin", "gabapentin"),
            Map.entry("lovenox", "enoxaparin"),
            Map.entry("zyloprim", "allopurinol"),
            Map.entry("diflucan", "fluconazole"),
            Map.entry("biaxin", "clarithromycin"),
            Map.entry("sporanox", "itraconazole"),
            Map.entry("zyvox", "linezolid"),
            Map.entry("trexall", "methotrexate")
    );

    /**
     * Ingredient → the classes it belongs to. Cross-reactivity is evaluated at class level, which
     * is the whole point: an allergy is to a structure, not to a string.
     */
    private static final Map<String, Set<String>> INGREDIENT_CLASSES = buildClasses();

    /**
     * Classes that share enough structure for an allergy to one to matter for the other, with the
     * note a clinician should see. Penicillin/cephalosporin is the canonical example: real
     * cross-reactivity is low (~1-2% for later generations) but it is not zero, and the decision
     * belongs to the prescriber rather than to a silent pass.
     */
    private static final Map<String, Map<String, String>> CROSS_REACTIVITY = Map.of(
            "penicillin", Map.of(
                    "cephalosporin", "Cephalosporins share a beta-lactam ring with penicillins. "
                                     + "Cross-reactivity is low (~1-2%) for 3rd generation and later, "
                                     + "but is not zero — confirm the nature of the documented reaction.",
                    "carbapenem", "Carbapenems are beta-lactams. Cross-reactivity with penicillin allergy "
                                  + "is under 1% but should be considered where the reaction was anaphylaxis."),
            "cephalosporin", Map.of(
                    "penicillin", "Penicillins share a beta-lactam ring with cephalosporins. "
                                  + "Confirm the nature of the documented reaction before prescribing."),
            "sulfonamide", Map.of(
                    "sulfonamide-nonantibiotic", "Non-antibiotic sulfonamides (furosemide, thiazides, "
                                                 + "celecoxib) are usually tolerated in sulfa-antibiotic allergy, "
                                                 + "but the shared moiety warrants a check.")
    );

    /**
     * Interaction pairs, keyed by an unordered pair of ingredient-or-class tokens. Severity and
     * mechanism are what makes the finding actionable — "interaction detected" is not.
     */
    private static final List<Interaction> INTERACTIONS = List.of(
            new Interaction("warfarin", "nsaid", DrugSafetyFinding.Severity.MAJOR,
                    "Substantially increased bleeding risk: NSAIDs inhibit platelet function and irritate "
                    + "gastric mucosa while warfarin is anticoagulating. Prefer acetaminophen for analgesia."),
            new Interaction("warfarin", "fluconazole", DrugSafetyFinding.Severity.MAJOR,
                    "Fluconazole inhibits CYP2C9 and can raise INR sharply within days. Monitor INR closely "
                    + "or select an alternative antifungal."),
            new Interaction("warfarin", "metronidazole", DrugSafetyFinding.Severity.MAJOR,
                    "Metronidazole potentiates warfarin and can produce a marked INR rise."),
            new Interaction("warfarin", "sulfamethoxazole-trimethoprim", DrugSafetyFinding.Severity.MAJOR,
                    "Co-trimoxazole displaces warfarin from protein binding and inhibits its metabolism; "
                    + "a substantial INR rise is expected."),
            new Interaction("warfarin", "amiodarone", DrugSafetyFinding.Severity.MAJOR,
                    "Amiodarone inhibits warfarin metabolism; the warfarin dose usually needs reducing by "
                    + "a third to a half."),
            new Interaction("ace-inhibitor", "potassium-sparing-diuretic", DrugSafetyFinding.Severity.MAJOR,
                    "Risk of significant hyperkalaemia. Check potassium and renal function before starting "
                    + "and within a week."),
            new Interaction("ace-inhibitor", "arb", DrugSafetyFinding.Severity.MAJOR,
                    "Dual RAAS blockade increases hyperkalaemia, hypotension and acute kidney injury without "
                    + "outcome benefit in most indications."),
            new Interaction("ace-inhibitor", "lithium", DrugSafetyFinding.Severity.MAJOR,
                    "ACE inhibitors reduce lithium clearance and can precipitate lithium toxicity."),
            new Interaction("lithium", "nsaid", DrugSafetyFinding.Severity.MAJOR,
                    "NSAIDs reduce renal lithium clearance and can precipitate toxicity."),
            new Interaction("simvastatin", "macrolide", DrugSafetyFinding.Severity.MAJOR,
                    "Strong CYP3A4 inhibition raises simvastatin exposure markedly, with a real risk of "
                    + "rhabdomyolysis. Suspend the statin for the antibiotic course or switch."),
            new Interaction("simvastatin", "itraconazole", DrugSafetyFinding.Severity.MAJOR,
                    "Contraindicated combination: azole CYP3A4 inhibition and statin myotoxicity."),
            new Interaction("atorvastatin", "clarithromycin", DrugSafetyFinding.Severity.MAJOR,
                    "CYP3A4 inhibition raises atorvastatin exposure; cap the dose or suspend during treatment."),
            new Interaction("ssri", "maoi", DrugSafetyFinding.Severity.CONTRAINDICATED,
                    "Serotonin syndrome. This combination requires a washout period, not monitoring."),
            new Interaction("ssri", "linezolid", DrugSafetyFinding.Severity.MAJOR,
                    "Linezolid is a reversible MAOI — serotonin syndrome risk."),
            new Interaction("ssri", "tramadol", DrugSafetyFinding.Severity.MAJOR,
                    "Additive serotonergic effect plus a lowered seizure threshold."),
            new Interaction("methotrexate", "sulfamethoxazole-trimethoprim", DrugSafetyFinding.Severity.MAJOR,
                    "Both are antifolates: additive myelosuppression, which can be severe."),
            new Interaction("methotrexate", "nsaid", DrugSafetyFinding.Severity.MAJOR,
                    "NSAIDs reduce methotrexate clearance; significant at antineoplastic doses."),
            new Interaction("opioid", "benzodiazepine", DrugSafetyFinding.Severity.MAJOR,
                    "Additive respiratory depression and sedation — a leading cause of prescription overdose "
                    + "death. Co-prescribe only with a documented indication and the lowest effective doses."),
            new Interaction("clopidogrel", "omeprazole", DrugSafetyFinding.Severity.MODERATE,
                    "Omeprazole inhibits CYP2C19 and reduces conversion of clopidogrel to its active "
                    + "metabolite. Pantoprazole is the usual substitute."),
            new Interaction("digoxin", "amiodarone", DrugSafetyFinding.Severity.MAJOR,
                    "Amiodarone raises digoxin levels; halve the digoxin dose and monitor."),
            new Interaction("digoxin", "furosemide", DrugSafetyFinding.Severity.MODERATE,
                    "Diuretic-induced hypokalaemia potentiates digoxin toxicity. Monitor potassium."),
            new Interaction("fluoroquinolone", "ondansetron", DrugSafetyFinding.Severity.MODERATE,
                    "Additive QT prolongation. Consider an ECG where other QT risk factors are present."),
            new Interaction("macrolide", "fluoroquinolone", DrugSafetyFinding.Severity.MODERATE,
                    "Additive QT prolongation."),
            new Interaction("metformin", "furosemide", DrugSafetyFinding.Severity.MODERATE,
                    "Diuretic-induced volume depletion raises the risk of metformin-associated lactic "
                    + "acidosis. Confirm renal function.")
    );

    /** Ingredient → maximum accepted total daily dose in milligrams for a standard adult. */
    private static final Map<String, Double> MAX_DAILY_MG = Map.ofEntries(
            Map.entry("acetaminophen", 4000.0),
            Map.entry("ibuprofen", 3200.0),
            Map.entry("naproxen", 1100.0),
            Map.entry("diclofenac", 150.0),
            Map.entry("ketorolac", 40.0),
            Map.entry("acetylsalicylic acid", 4000.0),
            Map.entry("amoxicillin", 4000.0),
            Map.entry("azithromycin", 500.0),
            Map.entry("ciprofloxacin", 1500.0),
            Map.entry("levofloxacin", 750.0),
            Map.entry("metronidazole", 4000.0),
            Map.entry("metformin", 2550.0),
            Map.entry("atorvastatin", 80.0),
            Map.entry("simvastatin", 40.0),
            Map.entry("rosuvastatin", 40.0),
            Map.entry("lisinopril", 80.0),
            Map.entry("losartan", 100.0),
            Map.entry("furosemide", 600.0),
            Map.entry("spironolactone", 400.0),
            Map.entry("sertraline", 200.0),
            Map.entry("fluoxetine", 80.0),
            Map.entry("escitalopram", 20.0),
            Map.entry("tramadol", 400.0),
            Map.entry("gabapentin", 3600.0),
            Map.entry("allopurinol", 800.0),
            Map.entry("ondansetron", 24.0),
            Map.entry("omeprazole", 40.0),
            Map.entry("prednisone", 80.0)
    );

    /**
     * Renally cleared drugs where the dose depends on eGFR. The system has no renal function for
     * the patient, so the honest output is a prompt to check rather than a silent pass.
     */
    private static final Set<String> REQUIRES_RENAL_ADJUSTMENT = Set.of(
            "metformin", "gabapentin", "enoxaparin", "allopurinol", "digoxin",
            "ciprofloxacin", "levofloxacin", "sulfamethoxazole-trimethoprim", "amoxicillin",
            "vancomycin", "lisinopril", "spironolactone"
    );

    private static Map<String, Set<String>> buildClasses() {
        Map<String, Set<String>> m = new HashMap<>();
        addClass(m, "penicillin", "penicillin", "amoxicillin", "ampicillin", "amoxicillin-clavulanate",
                "piperacillin", "piperacillin-tazobactam", "nafcillin", "dicloxacillin", "flucloxacillin",
                "benzylpenicillin", "phenoxymethylpenicillin");
        addClass(m, "cephalosporin", "cephalexin", "cefazolin", "cefuroxime", "ceftriaxone", "cefotaxime",
                "ceftazidime", "cefepime", "cefixime", "cefdinir", "cefpodoxime");
        addClass(m, "carbapenem", "meropenem", "imipenem", "ertapenem", "doripenem");
        addClass(m, "sulfonamide", "sulfamethoxazole", "sulfamethoxazole-trimethoprim", "sulfadiazine",
                "sulfasalazine");
        addClass(m, "sulfonamide-nonantibiotic", "furosemide", "hydrochlorothiazide", "celecoxib",
                "acetazolamide");
        addClass(m, "nsaid", "ibuprofen", "naproxen", "diclofenac", "ketorolac", "indomethacin",
                "celecoxib", "meloxicam", "acetylsalicylic acid", "etoricoxib");
        addClass(m, "macrolide", "azithromycin", "clarithromycin", "erythromycin", "roxithromycin");
        addClass(m, "fluoroquinolone", "ciprofloxacin", "levofloxacin", "moxifloxacin", "ofloxacin",
                "norfloxacin");
        addClass(m, "aminoglycoside", "gentamicin", "amikacin", "tobramycin", "streptomycin");
        addClass(m, "tetracycline", "doxycycline", "minocycline", "tetracycline", "tigecycline");
        addClass(m, "opioid", "morphine", "codeine", "oxycodone", "hydrocodone", "hydromorphone",
                "fentanyl", "tramadol", "buprenorphine", "methadone", "pethidine");
        addClass(m, "benzodiazepine", "diazepam", "lorazepam", "alprazolam", "clonazepam", "midazolam",
                "temazepam");
        addClass(m, "statin", "atorvastatin", "simvastatin", "rosuvastatin", "pravastatin", "lovastatin",
                "fluvastatin");
        addClass(m, "ace-inhibitor", "lisinopril", "enalapril", "ramipril", "captopril", "perindopril",
                "benazepril");
        addClass(m, "arb", "losartan", "valsartan", "telmisartan", "irbesartan", "candesartan", "olmesartan");
        addClass(m, "potassium-sparing-diuretic", "spironolactone", "eplerenone", "amiloride", "triamterene");
        addClass(m, "loop-diuretic", "furosemide", "bumetanide", "torsemide");
        addClass(m, "ssri", "sertraline", "fluoxetine", "escitalopram", "citalopram", "paroxetine",
                "fluvoxamine");
        addClass(m, "maoi", "phenelzine", "tranylcypromine", "isocarboxazid", "selegiline");
        addClass(m, "ppi", "omeprazole", "pantoprazole", "esomeprazole", "lansoprazole", "rabeprazole");
        addClass(m, "anticoagulant", "warfarin", "apixaban", "rivaroxaban", "dabigatran", "edoxaban",
                "enoxaparin", "heparin");
        return Map.copyOf(m);
    }

    private static void addClass(Map<String, Set<String>> m, String className, String... ingredients) {
        for (String ingredient : ingredients) {
            m.computeIfAbsent(ingredient, k -> new HashSet<>()).add(className);
        }
    }

    /** An interaction between two ingredient-or-class tokens. Order is not significant. */
    public record Interaction(String a, String b, DrugSafetyFinding.Severity severity, String mechanism) {
    }

    /**
     * Reduces free text to a normalised ingredient name.
     *
     * <p>Strips dose, form and packaging noise ("Amoxil 500mg capsules" → "amoxicillin") and
     * resolves brands. Returns the lower-cased, trimmed input when nothing is recognised, so an
     * unknown drug is still comparable to itself for duplicate detection.
     */
    public String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String s = raw.toLowerCase(Locale.ROOT).trim();

        // Drop anything from the first dose-like or form-like token onward.
        s = s.replaceAll("\\b\\d+(\\.\\d+)?\\s*(mg|mcg|g|ml|iu|units?|%)\\b.*", "");
        s = s.replaceAll("\\b(tablets?|tabs?|capsules?|caps?|syrup|suspension|injection|inj|iv|im|po|"
                         + "oral|topical|cream|ointment|drops?|solution|sr|xr|er|cr|dt)\\b.*", "");
        s = s.replaceAll("[^a-z\\-\\s]", " ").replaceAll("\\s+", " ").trim();

        if (s.isEmpty()) {
            return raw.toLowerCase(Locale.ROOT).trim();
        }

        if (BRAND_TO_INGREDIENT.containsKey(s)) {
            return BRAND_TO_INGREDIENT.get(s);
        }

        // "amoxicillin clavulanate" and "amoxicillin/clavulanate" both reach the hyphenated form.
        String hyphenated = s.replace(' ', '-');
        if (BRAND_TO_INGREDIENT.containsKey(hyphenated)) {
            return BRAND_TO_INGREDIENT.get(hyphenated);
        }
        if (INGREDIENT_CLASSES.containsKey(hyphenated)) {
            return hyphenated;
        }
        if (INGREDIENT_CLASSES.containsKey(s) || MAX_DAILY_MG.containsKey(s)) {
            return s;
        }

        // Last resort: a brand name with a trailing qualifier, e.g. "augmentin duo".
        for (String token : s.split(" ")) {
            if (BRAND_TO_INGREDIENT.containsKey(token)) {
                return BRAND_TO_INGREDIENT.get(token);
            }
            if (INGREDIENT_CLASSES.containsKey(token)) {
                return token;
            }
        }

        return s;
    }

    /** The classes an ingredient belongs to, or empty if it is not in the knowledge base. */
    public Set<String> classesOf(String ingredient) {
        return INGREDIENT_CLASSES.getOrDefault(ingredient, Set.of());
    }

    /** True when the knowledge base recognises this ingredient at all. */
    public boolean isKnown(String ingredient) {
        return INGREDIENT_CLASSES.containsKey(ingredient)
               || MAX_DAILY_MG.containsKey(ingredient)
               || BRAND_TO_INGREDIENT.containsValue(ingredient);
    }

    /** Classes that cross-react with the given class, mapped to the note explaining why. */
    public Map<String, String> crossReactiveWith(String className) {
        return CROSS_REACTIVITY.getOrDefault(className, Map.of());
    }

    public List<Interaction> interactions() {
        return INTERACTIONS;
    }

    public Optional<Double> maxDailyMg(String ingredient) {
        return Optional.ofNullable(MAX_DAILY_MG.get(ingredient));
    }

    public boolean requiresRenalAdjustment(String ingredient) {
        return REQUIRES_RENAL_ADJUSTMENT.contains(ingredient);
    }
}
