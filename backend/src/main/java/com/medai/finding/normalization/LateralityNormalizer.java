package com.medai.finding.normalization;

import com.medai.finding.model.AnatomicalSide;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class LateralityNormalizer {

    private static final Pattern RIGHT = Pattern.compile("(?i)(?<![A-Za-z0-9])(?:right|rt\\.?|r)(?![A-Za-z0-9])");
    private static final Pattern LEFT = Pattern.compile("(?i)(?<![A-Za-z0-9])(?:left|lt\\.?|l)(?![A-Za-z0-9])");
    private static final Pattern BILATERAL = Pattern.compile("(?i)(?<![A-Za-z0-9])bilateral(?:ly)?(?![A-Za-z0-9])");
    private static final Set<String> LATERALITY_TOKENS = Set.of("right", "rt", "r", "left", "lt", "l");

    private LateralityNormalizer() {
    }

    public static AnatomicalSide detect(String text) {
        if (text == null || text.isBlank()) {
            return AnatomicalSide.UNSPECIFIED;
        }
        if (BILATERAL.matcher(text).find()) {
            return AnatomicalSide.BILATERAL;
        }

        boolean right = RIGHT.matcher(text).find();
        boolean left = LEFT.matcher(text).find();
        if (right == left) {
            return AnatomicalSide.UNSPECIFIED;
        }
        return right ? AnatomicalSide.RIGHT : AnatomicalSide.LEFT;
    }

    public static Optional<AnatomicalSide> unilateralSide(String text) {
        AnatomicalSide side = detect(text);
        return side == AnatomicalSide.RIGHT || side == AnatomicalSide.LEFT
                ? Optional.of(side)
                : Optional.empty();
    }

    public static boolean isLateralityToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String normalized = token.toLowerCase(Locale.ROOT).replace(".", "");
        return LATERALITY_TOKENS.contains(normalized);
    }
}
