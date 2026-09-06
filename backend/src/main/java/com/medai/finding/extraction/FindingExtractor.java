package com.medai.finding.extraction;

import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.StructuredFinding;

import java.util.List;

public interface FindingExtractor {
    List<StructuredFinding> extract(FindingSourceSection sourceSection, String text);
}
