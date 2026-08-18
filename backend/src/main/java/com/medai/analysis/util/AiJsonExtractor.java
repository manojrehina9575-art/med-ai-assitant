package com.medai.analysis.util;

/**
 * Extracts a JSON object from a raw LLM response.
 * <p>
 * Models sometimes wrap JSON in markdown fences, add prose before/after it, or (on an
 * upstream gateway error) return an HTML page. This isolates the outermost {@code { ... }}
 * object so parsing does not fail on incidental surrounding text.
 */
public final class AiJsonExtractor {

    private AiJsonExtractor() {
    }

    /**
     * Returns the substring from the first {@code '{'} to the last {@code '}'} after
     * stripping markdown code fences. Throws {@link IllegalArgumentException} with a short,
     * safe snippet if no JSON object is present (e.g. an HTML error page).
     */
    public static String extractJsonObject(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("AI returned an empty response");
        }

        // Reasoning models (e.g. Qwen) emit a <think>...</think> chain-of-thought before the
        // answer. Remove complete blocks, and any unterminated trailing block, so the JSON
        // that follows can be isolated.
        String withoutThink = content.replaceAll("(?is)<think>.*?</think>", "");
        int danglingThink = withoutThink.indexOf("<think>");
        if (danglingThink >= 0) {
            withoutThink = withoutThink.substring(0, danglingThink);
        }

        String stripped = withoutThink.strip();
        if (stripped.startsWith("```json")) {
            stripped = stripped.substring(7);
        } else if (stripped.startsWith("```")) {
            stripped = stripped.substring(3);
        }
        if (stripped.endsWith("```")) {
            stripped = stripped.substring(0, stripped.length() - 3);
        }
        stripped = stripped.strip();

        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start < 0 || end <= start) {
            String snippet = stripped.length() > 200 ? stripped.substring(0, 200) + "…" : stripped;
            throw new IllegalArgumentException(
                    "AI response did not contain a JSON object. Response began with: " + snippet);
        }
        return stripped.substring(start, end + 1);
    }
}
