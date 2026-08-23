package com.medai.export.service;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final PatientRepository patientRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("UTC"));

    /**
     * Exports a single analysis report as a styled PDF.
     *
     * @return raw PDF bytes to be streamed as a download
     */
    @Transactional(readOnly = true)
    public byte[] exportAnalysisReportPdf(UUID analysisId) throws IOException {
        UUID tenantId = TenantContext.requireTenantId();

        AnalysisRequest analysis = analysisRequestRepository
                .findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));

        Patient patient = patientRepository
                .findByIdAndTenantId(analysis.getPatientId(), tenantId)
                .orElse(null);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;
                float pageWidth = page.getMediaBox().getWidth() - 2 * margin;

                // ── Header banner ───────────────────────────────────────
                cs.setNonStrokingColor(0.06f, 0.09f, 0.18f); // dark navy
                cs.addRect(0, y - 10, page.getMediaBox().getWidth(), 60);
                cs.fill();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                cs.setNonStrokingColor(1, 1, 1);
                cs.newLineAtOffset(margin, y + 25);
                cs.showText("Med-AI Clinical Analysis Report");
                cs.endText();

                y -= 30;

                // ── Divider ──────────────────────────────────────────────
                cs.setStrokingColor(0.22f, 0.45f, 0.9f);
                cs.setLineWidth(2);
                cs.moveTo(margin, y);
                cs.lineTo(margin + pageWidth, y);
                cs.stroke();
                y -= 20;

                // ── Report metadata ──────────────────────────────────────
                cs.setNonStrokingColor(0, 0, 0);
                writeLabel(cs, margin, y, "Report ID:", analysis.getId().toString());
                y -= 16;
                writeLabel(cs, margin, y, "Generated:", DATE_FMT.format(java.time.Instant.now()));
                y -= 16;
                writeLabel(cs, margin, y, "Analysis Type:", analysis.getAnalysisType().name());
                y -= 16;
                writeLabel(cs, margin, y, "Status:", analysis.getStatus().name());
                y -= 16;
                if (analysis.getProcessingCompletedAt() != null) {
                    writeLabel(cs, margin, y, "Completed At:", DATE_FMT.format(analysis.getProcessingCompletedAt()));
                    y -= 16;
                }
                writeLabel(cs, margin, y, "Model Used:", analysis.getModelUsed() != null ? analysis.getModelUsed() : "—");
                y -= 24;

                // ── Patient info ─────────────────────────────────────────
                cs.setNonStrokingColor(0.22f, 0.45f, 0.9f);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Patient Information");
                cs.endText();
                y -= 16;

                cs.setNonStrokingColor(0, 0, 0);
                if (patient != null) {
                    writeLabel(cs, margin, y, "Name:", patient.getFullName());
                    y -= 16;
                    writeLabel(cs, margin, y, "MRN:", patient.getMedicalRecordNumber());
                    y -= 16;
                    writeLabel(cs, margin, y, "DOB:", patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : "—");
                    y -= 16;
                    writeLabel(cs, margin, y, "Gender:", patient.getGender() != null ? patient.getGender().name() : "—");
                    y -= 16;
                    writeLabel(cs, margin, y, "Blood Group:", patient.getBloodGroup() != null ? patient.getBloodGroup() : "—");
                    y -= 24;
                } else {
                    writeLabel(cs, margin, y, "Patient:", "Information not available");
                    y -= 24;
                }

                // ── Clinical notes ───────────────────────────────────────
                if (analysis.getClinicalNotes() != null && !analysis.getClinicalNotes().isBlank()) {
                    cs.setNonStrokingColor(0.22f, 0.45f, 0.9f);
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Clinical Notes");
                    cs.endText();
                    y -= 16;

                    cs.setNonStrokingColor(0, 0, 0);
                    y = writeWrappedText(cs, margin, y, pageWidth, analysis.getClinicalNotes(), 10);
                    y -= 16;
                }

                // ── AI Result ────────────────────────────────────────────
                cs.setNonStrokingColor(0.22f, 0.45f, 0.9f);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("AI Analysis Result");
                cs.endText();
                y -= 16;

                cs.setNonStrokingColor(0, 0, 0);
                String resultText = analysis.getResult() != null ? analysis.getResult() : "No result available";
                // Trim JSON to readable form (show raw JSON)
                if (resultText.length() > 1800) {
                    resultText = resultText.substring(0, 1800) + "\n[truncated — see full report in app]";
                }
                writeWrappedText(cs, margin, y, pageWidth, resultText, 9);

                // ── Footer ───────────────────────────────────────────────
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8);
                cs.beginText();
                cs.newLineAtOffset(margin, 30);
                cs.showText("DISCLAIMER: This report is AI-generated and requires clinician review before clinical decisions are made.");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Exports the full patient registry as CSV.
     */
    @Transactional(readOnly = true)
    public byte[] exportPatientsCsv() {
        UUID tenantId = TenantContext.requireTenantId();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Header
        pw.println("MRN,FirstName,LastName,DateOfBirth,Gender,BloodGroup,Phone,Email,Active");

        // Paginate to handle large datasets
        int page = 0;
        int size = 500;
        Page<Patient> result;
        do {
            result = patientRepository.findByTenantId(tenantId, PageRequest.of(page++, size));
            for (Patient p : result.getContent()) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csvEsc(p.getMedicalRecordNumber()),
                        csvEsc(p.getFirstName()),
                        csvEsc(p.getLastName()),
                        p.getDateOfBirth() != null ? p.getDateOfBirth() : "",
                        p.getGender() != null ? p.getGender().name() : "",
                        csvEsc(p.getBloodGroup()),
                        csvEsc(p.getPhone()),
                        csvEsc(p.getEmail()),
                        Boolean.TRUE.equals(p.getIsActive()) ? "Yes" : "No"
                );
            }
        } while (result.hasNext());

        return sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── helpers ─────────────────────────────────────────────

    private String csvEsc(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private void writeLabel(PDPageContentStream cs, float x, float y,
                            String label, String value) throws IOException {
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(label + " ");
        cs.endText();

        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        cs.beginText();
        cs.newLineAtOffset(x + 90, y);
        cs.showText(value);
        cs.endText();
    }

    private float writeWrappedText(PDPageContentStream cs, float x, float y,
                                   float maxWidth, String text, float fontSize) throws IOException {
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        float lineHeight = fontSize + 3;
        // Simple word-wrap by characters (~100 chars per line at 9pt with 500pt width)
        int charsPerLine = (int) (maxWidth / (fontSize * 0.5));

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() + word.length() + 1 > charsPerLine) {
                cs.beginText();
                cs.newLineAtOffset(x, y);
                cs.showText(line.toString().trim());
                cs.endText();
                y -= lineHeight;
                if (y < 60) break; // prevent overflow off page
                line = new StringBuilder();
            }
            line.append(word).append(' ');
        }
        if (!line.isEmpty() && y > 60) {
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(line.toString().trim());
            cs.endText();
            y -= lineHeight;
        }
        return y;
    }
}
