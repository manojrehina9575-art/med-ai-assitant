import * as dicomParser from 'dicom-parser';

export type FileCategory = 'dicom' | 'image' | 'pdf' | 'text' | 'unsupported';

export interface DicomMetadata {
  rows?: number;
  columns?: number;
  patientName?: string;
  modality?: string;
  studyDate?: string;
  photometricInterpretation?: string;
  windowCenter?: number;
  windowWidth?: number;
  bitsAllocated?: number;
  manufacturer?: string;
}

export interface RenderedMedicalFile {
  category: FileCategory;
  url: string;
  textData?: string;
  metadata?: DicomMetadata;
}

export const DICOM_PRESETS: Record<string, { wc: number; ww: number; label: string }> = {
  DEFAULT: { wc: 40, ww: 400, label: 'Default' },
  LUNG: { wc: -600, ww: 1500, label: 'Lung (-600/1500)' },
  BONE: { wc: 300, ww: 1500, label: 'Bone (300/1500)' },
  SOFT_TISSUE: { wc: 40, ww: 350, label: 'Soft Tissue (40/350)' },
  BRAIN: { wc: 40, ww: 80, label: 'Brain (40/80)' },
};

/**
 * Detects file category by extension and mime type
 */
export function detectFileCategory(fileName: string, mimeType?: string): FileCategory {
  const ext = fileName.toLowerCase().split('.').pop() || '';
  const mime = (mimeType || '').toLowerCase();

  if (ext === 'dcm' || ext === 'dicom' || mime.includes('dicom')) {
    return 'dicom';
  }
  if (['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp', 'tiff', 'svg'].includes(ext) || mime.startsWith('image/')) {
    return 'image';
  }
  if (ext === 'pdf' || mime.includes('pdf')) {
    return 'pdf';
  }
  if (['txt', 'csv', 'json', 'xml', 'log'].includes(ext) || mime.startsWith('text/')) {
    return 'text';
  }
  return 'unsupported';
}

/**
 * Processes any medical file blob (DICOM, standard image, PDF, text) into a visual renderable format.
 */
export async function processMedicalFile(
  blob: Blob,
  fileName: string,
  customWc?: number,
  customWw?: number
): Promise<RenderedMedicalFile> {
  const category = detectFileCategory(fileName, blob.type);

  if (category === 'pdf') {
    return {
      category: 'pdf',
      url: URL.createObjectURL(blob),
    };
  }

  if (category === 'text') {
    const textData = await blob.text();
    return {
      category: 'text',
      url: '',
      textData,
    };
  }

  const arrayBuffer = await blob.arrayBuffer();
  const byteArray = new Uint8Array(arrayBuffer);

  // Check for DICOM magic header
  let isDicom = category === 'dicom';
  if (!isDicom && byteArray.length > 132) {
    const magic = String.fromCharCode(byteArray[128], byteArray[129], byteArray[130], byteArray[131]);
    if (magic === 'DICM') {
      isDicom = true;
    }
  }

  if (isDicom) {
    try {
      const dataSet = dicomParser.parseDicom(byteArray);
      const rows = dataSet.uint16('x00280010') || 512;
      const columns = dataSet.uint16('x00280011') || 512;
      const bitsAllocated = dataSet.uint16('x00280100') || 16;
      const pixelRepresentation = dataSet.uint16('x00280103') || 0;
      const photometricInterpretation = dataSet.string('x00280004') || 'MONOCHROME2';
      const parsedWc = dataSet.floatString('x00281050');
      const parsedWw = dataSet.floatString('x00281051');
      const modality = dataSet.string('x00080060') || 'CR';
      const patientName = dataSet.string('x00100010') || 'Anonymous';
      const studyDate = dataSet.string('x00080020');
      const manufacturer = dataSet.string('x00080070');

      const pixelElement = dataSet.elements.x7fe00010;
      if (!pixelElement || pixelElement.length === 0) {
        throw new Error('DICOM dataset missing pixel data tag');
      }

      // Create Canvas & LUT mapping
      const canvas = document.createElement('canvas');
      canvas.width = columns;
      canvas.height = rows;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('Could not create canvas 2D context');

      const imageData = ctx.createImageData(columns, rows);
      const data = imageData.data;
      const numPixels = rows * columns;

      let min = Number.MAX_VALUE;
      let max = Number.MIN_VALUE;

      if (bitsAllocated === 8) {
        const pixels = new Uint8Array(arrayBuffer, pixelElement.dataOffset, numPixels);
        for (let i = 0; i < numPixels; i++) {
          const val = pixels[i];
          if (val < min) min = val;
          if (val > max) max = val;
        }

        const wc = customWc ?? parsedWc ?? (min + max) / 2;
        const ww = customWw ?? parsedWw ?? (max - min || 255);
        const low = wc - ww / 2;
        const high = wc + ww / 2;

        for (let i = 0; i < numPixels; i++) {
          let intensity = ((pixels[i] - low) / (high - low)) * 255;
          intensity = Math.min(Math.max(intensity, 0), 255);
          if (photometricInterpretation === 'MONOCHROME1') {
            intensity = 255 - intensity;
          }

          const idx = i * 4;
          data[idx] = intensity;
          data[idx + 1] = intensity;
          data[idx + 2] = intensity;
          data[idx + 3] = 255;
        }
      } else {
        // 16-bit signed or unsigned
        const pixels = pixelRepresentation === 1
          ? new Int16Array(arrayBuffer, pixelElement.dataOffset, numPixels)
          : new Uint16Array(arrayBuffer, pixelElement.dataOffset, numPixels);

        for (let i = 0; i < numPixels; i++) {
          const val = pixels[i];
          if (val < min) min = val;
          if (val > max) max = val;
        }

        const wc = customWc ?? parsedWc ?? (min + max) / 2;
        const ww = customWw ?? parsedWw ?? (max - min || 1000);
        const low = wc - ww / 2;
        const high = wc + ww / 2;

        for (let i = 0; i < numPixels; i++) {
          let intensity = ((pixels[i] - low) / (high - low)) * 255;
          intensity = Math.min(Math.max(intensity, 0), 255);
          if (photometricInterpretation === 'MONOCHROME1') {
            intensity = 255 - intensity;
          }

          const idx = i * 4;
          data[idx] = intensity;
          data[idx + 1] = intensity;
          data[idx + 2] = intensity;
          data[idx + 3] = 255;
        }
      }

      ctx.putImageData(imageData, 0, 0);

      return {
        category: 'dicom',
        url: canvas.toDataURL('image/png'),
        metadata: {
          rows,
          columns,
          patientName,
          modality,
          studyDate,
          photometricInterpretation,
          windowCenter: parsedWc,
          windowWidth: parsedWw,
          bitsAllocated,
          manufacturer,
        },
      };
    } catch (e) {
      console.warn('DICOM parsing error, falling back to standard image render:', e);
    }
  }

  // Standard visual images (PNG, JPEG, WebP, TIFF)
  return {
    category: 'image',
    url: URL.createObjectURL(blob),
  };
}
