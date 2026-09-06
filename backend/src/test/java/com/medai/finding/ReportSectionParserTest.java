package com.medai.finding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.finding.extraction.ReportSectionParser;
import com.medai.finding.extraction.ReportSectionText;
import com.medai.finding.model.FindingSourceSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportSectionParserTest {

    private final ReportSectionParser parser = new ReportSectionParser(new ObjectMapper());

    @Test
    @DisplayName("parses pasted headed report text into findings, comparison and impression")
    void parsesPastedHeadedReportText() {
        String source = """
                FINDINGS:
                There is a comminuted fracture involving the proximal right humerus.

                COMPARISON:
                No prior study available.

                IMPRESSION:
                Comminuted fracture of the proximal left humerus.
                """;

        List<ReportSectionText> sections = parser.parse(source).stream()
                .map(section -> new ReportSectionText(section.sourceSection(), section.text().strip()))
                .toList();

        assertThat(sections).containsExactly(
                new ReportSectionText(FindingSourceSection.FINDINGS,
                        "There is a comminuted fracture involving the proximal right humerus."),
                new ReportSectionText(FindingSourceSection.COMPARISON, "No prior study available."),
                new ReportSectionText(FindingSourceSection.IMPRESSION,
                        "Comminuted fracture of the proximal left humerus."));
    }
}
