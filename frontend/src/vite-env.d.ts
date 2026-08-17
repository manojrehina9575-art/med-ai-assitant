/// <reference types="vite/client" />

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
