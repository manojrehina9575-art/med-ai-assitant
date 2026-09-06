package com.medai.qa.engine;

import com.medai.qa.model.QaIssue;
import com.medai.qa.model.QaReportText;
import com.medai.qa.model.QaResult;
import com.medai.qa.rules.LateralityRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QaEngine {

    private final LateralityRule lateralityRule;

    public QaResult evaluate(UUID reportId, QaReportText reportText) {
        List<QaIssue> issues = new ArrayList<>();
        issues.addAll(lateralityRule.evaluate(reportText.findings(), reportText.impression()));
        return QaResult.from(reportId, issues, Instant.now());
    }
}
