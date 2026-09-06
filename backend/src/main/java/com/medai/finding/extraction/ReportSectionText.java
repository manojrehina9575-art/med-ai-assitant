package com.medai.finding.extraction;

import com.medai.finding.model.FindingSourceSection;

public record ReportSectionText(
        FindingSourceSection sourceSection,
        String text
) {
    public ReportSectionText {
        sourceSection = sourceSection == null ? FindingSourceSection.UNKNOWN : sourceSection;
        text = text == null ? "" : text.strip();
    }
}
