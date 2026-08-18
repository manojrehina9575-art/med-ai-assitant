package com.medai.analysis.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stored medical file into an input the model can genuinely read, or fails.
 *
 * <p>Dispatch rules:
 * <ul>
 *   <li><b>PDF with a text layer</b> → extracted text ({@link Modality#TEXT}). More accurate and
 *       far cheaper than rasterising, and it is what most lab-information systems emit.</li>
 *   <li><b>Scanned PDF</b> (no usable text layer) → each page rendered to a JPEG
 *       ({@link Modality#VISION}), bounded by {@link #MAX_SCANNED_PDF_PAGES}.</li>
 *   <li><b>JPEG / PNG / DICOM</b> → downscaled JPEG ({@link Modality#VISION}).</li>
 *   <li><b>Plain text / CSV</b> → contents ({@link Modality#TEXT}).</li>
 *   <li><b>Anything else</b> → {@link UnreadableInputException}.</li>
 * </ul>
 *
 * <p>There is deliberately no "give up and let the model guess from the filename" path. A model
 * asked to analyse a study it cannot see will invent findings, severities, and confidence scores
 * that read exactly like real ones.
 */
@Slf4j
public final class AnalysisInputPreparer {

    /** A text layer shorter than this is treated as incidental (page furniture, not content). */
    private static final int MIN_TEXT_LAYER_CHARS = 200;

    /** Rasterising many pages blows the provider's per-minute token ceiling. */
    private static final int MAX_SCANNED_PDF_PAGES = 3;

    /** Guards against feeding an entire textbook into a lab-report prompt. */
    private static final int MAX_TEXT_CHARS = 20_000;

    private static final int PDF_RENDER_DPI = 150;

    private AnalysisInputPreparer() {
    }

    public enum Modality {
        /** The model was given the actual pixels. */
        VISION,
        /** The model was given text extracted from the document. */
        TEXT
    }

    /**
     * A verified-readable analysis input.
     *
     * @param modality how the model will actually receive the content
     * @param images   one or more images, non-empty when modality is VISION
     * @param mimeType mime type of {@code images}, null when modality is TEXT
     * @param text     extracted document text, non-null when modality is TEXT
     */
    public record PreparedInput(Modality modality, List<Resource> images, MimeType mimeType, String text) {

        public boolean isVision() {
            return modality == Modality.VISION;
        }
    }

    /**
     * @throws UnreadableInputException if the file cannot be read in a way the model can use
     */
    public static PreparedInput prepare(Resource source, MimeType declaredMime, String fileName) {
        byte[] bytes = readBytes(source, fileName);
        String name = fileName != null ? fileName.toLowerCase() : "";
        String mime = declaredMime != null ? declaredMime.toString().toLowerCase() : "";

        if (isPdf(bytes, name, mime)) {
            return preparePdf(bytes, fileName);
        }
        if (isPlainText(name, mime)) {
            return textInput(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), fileName);
        }

        // Raster or DICOM — throws if the bytes decode as neither.
        ImagePreprocessor.PreparedImage image = ImagePreprocessor.prepareForVision(bytes, declaredMime);
        return new PreparedInput(Modality.VISION, List.of(image.resource()), image.mimeType(), null);
    }

    private static PreparedInput preparePdf(byte[] bytes, String fileName) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            String trimmed = text != null ? text.strip() : "";

            if (trimmed.length() >= MIN_TEXT_LAYER_CHARS) {
                log.info("PDF '{}' has a text layer ({} chars) — analysing extracted text", fileName, trimmed.length());
                return textInput(trimmed, fileName);
            }

            int pages = document.getNumberOfPages();
            if (pages == 0) {
                throw new UnreadableInputException("The PDF '" + fileName + "' contains no pages.");
            }
            if (pages > MAX_SCANNED_PDF_PAGES) {
                throw new UnreadableInputException(
                        "The PDF '" + fileName + "' is a scanned document with " + pages + " pages, above the "
                        + MAX_SCANNED_PDF_PAGES + "-page limit for image analysis. Upload a PDF with a text layer, "
                        + "or split it into separate files.");
            }

            log.info("PDF '{}' has no usable text layer — rendering {} page(s) at {} DPI for vision analysis",
                    fileName, pages, PDF_RENDER_DPI);

            PDFRenderer renderer = new PDFRenderer(document);
            List<Resource> images = new ArrayList<>(pages);
            for (int page = 0; page < pages; page++) {
                BufferedImage rendered = renderer.renderImageWithDPI(page, PDF_RENDER_DPI);
                images.add(ImagePreprocessor.fromRaster(rendered).resource());
            }
            return new PreparedInput(Modality.VISION, List.copyOf(images), ImagePreprocessor.JPEG, null);

        } catch (UnreadableInputException e) {
            throw e;
        } catch (IOException e) {
            throw new UnreadableInputException("The PDF '" + fileName + "' could not be opened: " + e.getMessage(), e);
        }
    }

    private static PreparedInput textInput(String text, String fileName) {
        String trimmed = text != null ? text.strip() : "";
        if (trimmed.isBlank()) {
            throw new UnreadableInputException("The document '" + fileName + "' contains no readable text.");
        }
        if (trimmed.length() > MAX_TEXT_CHARS) {
            throw new UnreadableInputException(
                    "The document '" + fileName + "' contains " + trimmed.length() + " characters, above the "
                    + MAX_TEXT_CHARS + "-character limit for a single analysis. Split it into separate reports.");
        }
        return new PreparedInput(Modality.TEXT, List.of(), null, trimmed);
    }

    private static boolean isPdf(byte[] bytes, String name, String mime) {
        boolean magic = bytes.length > 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        return magic || name.endsWith(".pdf") || mime.contains("pdf");
    }

    private static boolean isPlainText(String name, String mime) {
        return mime.startsWith("text/")
               || name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".md");
    }

    private static byte[] readBytes(Resource source, String fileName) {
        try {
            return source.getContentAsByteArray();
        } catch (IOException e) {
            throw new UnreadableInputException(
                    "The stored file for '" + fileName + "' could not be read: " + e.getMessage(), e);
        }
    }
}
