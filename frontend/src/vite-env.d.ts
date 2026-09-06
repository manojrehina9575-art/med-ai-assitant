/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Optional path/URL override for the bundled licensed skeleton GLB. "none"/"off" forces fallback. */
  readonly VITE_ANATOMY_SKELETON_MODEL_URL?: string;
  /** Optional attribution override; the bundled model metadata supplies the verified default. */
  readonly VITE_ANATOMY_SKELETON_MODEL_ATTRIBUTION?: string;
  /** Optional license override; the bundled model metadata supplies the verified default. */
  readonly VITE_ANATOMY_SKELETON_MODEL_LICENSE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module 'dicom-parser' {
  interface DicomElement {
    tag: string;
    vr: string;
    length: number;
    dataOffset: number;
  }

  interface DataSet {
    elements: Record<string, DicomElement>;
    string(tag: string): string | undefined;
    uint16(tag: string): number | undefined;
    int16(tag: string): number | undefined;
    floatString(tag: string): number | undefined;
    intString(tag: string): number | undefined;
  }

  function parseDicom(byteArray: Uint8Array, options?: Record<string, unknown>): DataSet;
}
