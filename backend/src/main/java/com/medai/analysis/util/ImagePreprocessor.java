package com.medai.analysis.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * Decodes and normalises raster images for multimodal (vision) LLM calls.
 *
 * <p>Vision providers reject base64 payloads above a few MB, so images are downscaled and
 * re-encoded as JPEG to keep the request comfortably within provider limits.
 *
 * <p>Unlike an earlier version of this class, decode failures are <b>not</b> swallowed. If the
 * bytes cannot be decoded into a raster image, an {@link UnreadableInputException} is thrown so
 * the caller fails the analysis instead of sending unreadable bytes and accepting whatever the
 * model says about them.
 */
@Slf4j
public final class ImagePreprocessor {

    /** Longest-edge cap; keeps detail while bounding payload size. */
    private static final int MAX_DIMENSION = 1568;
    private static final float JPEG_QUALITY = 0.85f;
    public static final MimeType JPEG = MimeType.valueOf("image/jpeg");

    private ImagePreprocessor() {
    }

    public record PreparedImage(Resource resource, MimeType mimeType) {
    }

    /**
     * Decodes (raster or DICOM), downscales, and re-encodes as JPEG.
     *
     * @throws UnreadableInputException if the bytes are not a decodable image
     */
    public static PreparedImage prepareForVision(byte[] bytes, MimeType declaredMime) {
        BufferedImage decoded = decode(bytes);
        if (decoded == null) {
            throw new UnreadableInputException(
                    "The file could not be decoded as an image (declared type: " + declaredMime + "). "
                    + "Supported formats are JPEG, PNG, and DICOM, plus PDF via document extraction.");
        }
        return fromRaster(decoded);
    }

    /** Downscales an already-decoded raster (e.g. a rendered PDF page) and encodes it as JPEG. */
    public static PreparedImage fromRaster(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(w, h));

        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));

        // Draw onto an RGB canvas (drops any alpha channel; JPEG has no alpha).
        BufferedImage rgb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, targetW, targetH, null);
        g.dispose();

        byte[] jpeg = encodeJpeg(rgb);
        log.info("Prepared image for vision: {}x{} -> {}x{}, {} bytes JPEG", w, h, targetW, targetH, jpeg.length);
        return new PreparedImage(new ByteArrayResource(jpeg), JPEG);
    }

    /** Decodes standard raster formats, falling back to DICOM. Returns null if neither applies. */
    static BufferedImage decode(byte[] bytes) {
        BufferedImage raster = decodeRaster(bytes);
        return raster != null ? raster : decodeDicom(bytes);
    }

    private static BufferedImage decodeRaster(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decodes a DICOM file's first frame via the dcm4che ImageIO plugin, applying the stored
     * VOI LUT / window so the rendered image is diagnostically meaningful. Returns null if the
     * bytes are not DICOM or the transfer syntax cannot be decoded (e.g. a compressed syntax
     * whose codec is unavailable).
     */
    private static BufferedImage decodeDicom(byte[] bytes) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(iis);
            return reader.read(0, reader.getDefaultReadParam());
        } catch (Exception e) {
            log.warn("DICOM decode failed ({})", e.getMessage());
            return null;
        } finally {
            reader.dispose();
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG ImageWriter available in this JVM");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new UnreadableInputException("Failed to re-encode image for analysis: " + e.getMessage(), e);
        } finally {
            writer.dispose();
        }
    }
}
