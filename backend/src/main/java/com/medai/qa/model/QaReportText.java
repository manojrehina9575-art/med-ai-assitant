package com.medai.qa.model;

import java.util.List;

public record QaReportText(
        List<String> findings,
        String impression
) {
    public QaReportText {
        findings = findings == null ? List.of() : List.copyOf(findings);
        impression = impression == null ? "" : impression;
    }
}
