import React, { useState, useEffect, useRef, useCallback } from 'react';
import { fileService } from '@/services/fileService';
import {
  processMedicalFile,
  detectFileCategory,
  DICOM_PRESETS,
  type RenderedMedicalFile,
} from '@/utils/dicomRenderer';
import { Button } from '@/components/ui/Button';
import {
  ZoomIn,
  ZoomOut,
  RotateCw,
  Sun,
  Contrast,
  RotateCcw,
  Maximize2,
  Minimize2,
  Eye,
  Loader2,
  AlertCircle,
  Sparkles,
  FileText,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  Columns2,
  Layers,
  Sliders,
} from 'lucide-react';
import type { MedicalFile } from '@/types';

interface ImageViewerProps {
  patientId: string;
  fileId: string;
  fileName: string;
  fileType?: string;
  fileList?: MedicalFile[];
  onSelectFile?: (fileId: string) => void;
  className?: string;
}

export function ImageViewer({
  patientId,
  fileId,
  fileName,
  fileType = 'X-Ray',
  fileList = [],
  onSelectFile,
  className = '',
}: ImageViewerProps) {
  // Primary Viewer State
  const [renderedFile, setRenderedFile] = useState<RenderedMedicalFile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // DICOM Window Preset
  const [selectedPreset, setSelectedPreset] = useState<string>('DEFAULT');
  const [customWc, setCustomWc] = useState<number | undefined>(undefined);
  const [customWw, setCustomWw] = useState<number | undefined>(undefined);

  // View Transformation Controls
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [brightness, setBrightness] = useState(100);
  const [contrast, setContrast] = useState(100);
  const [inverted, setInverted] = useState(false);
  const [rotation, setRotation] = useState(0);
  const [isFullscreen, setIsFullscreen] = useState(false);

  // Split-Screen Comparison Mode
  const [compareMode, setCompareMode] = useState(false);
  const [compareFileId, setCompareFileId] = useState<string>('');
  const [compareRendered, setCompareRendered] = useState<RenderedMedicalFile | null>(null);
  const [compareLoading, setCompareLoading] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);

  // Load Primary File
  const loadPrimaryFile = useCallback(
    async (fId: string, fName: string, wc?: number, ww?: number) => {
      if (!patientId || !fId) return;
      setLoading(true);
      setError(null);
      try {
        const blob = await fileService.getFileBlob(patientId, fId);
        const result = await processMedicalFile(blob, fName, wc, ww);
        setRenderedFile(result);
      } catch (err) {
        console.error('Failed to load medical study:', err);
        setError('Failed to load medical study file');
      } finally {
        setLoading(false);
      }
    },
    [patientId]
  );

  useEffect(() => {
    loadPrimaryFile(fileId, fileName, customWc, customWw);
  }, [fileId, fileName, customWc, customWw, loadPrimaryFile]);

  // Load Compare File
  useEffect(() => {
    if (!compareMode || !compareFileId || !patientId) return;
    const compObj = fileList.find((f) => f.id === compareFileId);
    if (!compObj) return;

    setCompareLoading(true);
    fileService
      .getFileBlob(patientId, compareFileId)
      .then(async (blob) => {
        const result = await processMedicalFile(blob, compObj.originalFileName);
        setCompareRendered(result);
      })
      .catch(() => {})
      .finally(() => setCompareLoading(false));
  }, [compareMode, compareFileId, patientId, fileList]);

  // Handle Preset Change for DICOM
  const handlePresetSelect = (presetKey: string) => {
    setSelectedPreset(presetKey);
    const preset = DICOM_PRESETS[presetKey];
    if (preset) {
      setCustomWc(preset.wc);
      setCustomWw(preset.ww);
    }
  };

  // Mouse Pan & Drag
  const handleMouseDown = (e: React.MouseEvent) => {
    if (e.button !== 0) return;
    setIsDragging(true);
    setDragStart({ x: e.clientX - position.x, y: e.clientY - position.y });
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    setPosition({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y,
    });
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  // Wheel Zoom
  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.15 : 0.85;
    setZoom((prev) => Math.min(Math.max(prev * factor, 0.4), 8));
  };

  // Reset View
  const handleReset = () => {
    setZoom(1);
    setPosition({ x: 0, y: 0 });
    setBrightness(100);
    setContrast(100);
    setInverted(false);
    setRotation(0);
    setSelectedPreset('DEFAULT');
    setCustomWc(undefined);
    setCustomWw(undefined);
  };

  // Toggle Fullscreen
  const toggleFullscreen = () => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      containerRef.current.requestFullscreen().catch(() => {});
      setIsFullscreen(true);
    } else {
      document.exitFullscreen().catch(() => {});
      setIsFullscreen(false);
    }
  };

  // Multi-File Navigation
  const currentIndex = fileList.findIndex((f) => f.id === fileId);
  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex >= 0 && currentIndex < fileList.length - 1;

  const handlePrev = () => {
    if (hasPrev && onSelectFile) {
      onSelectFile(fileList[currentIndex - 1].id);
    }
  };

  const handleNext = () => {
    if (hasNext && onSelectFile) {
      onSelectFile(fileList[currentIndex + 1].id);
    }
  };

  // Category & Format helper
  const category = renderedFile?.category || detectFileCategory(fileName);

  return (
    <div
      ref={containerRef}
      className={`relative flex flex-col overflow-hidden rounded-xl border border-slate-800 bg-slate-950 text-slate-100 shadow-2xl transition-all ${
        isFullscreen ? 'fixed inset-0 z-50 rounded-none h-screen' : 'h-[520px]'
      } ${className}`}
    >
      {/* 1. Multi-Study Thumbnail Filmstrip (When multiple files exist) */}
      {fileList.length > 1 && (
        <div className="flex items-center gap-2 overflow-x-auto border-b border-slate-800/80 bg-slate-900/90 px-3 py-2 scrollbar-thin scrollbar-thumb-slate-700">
          <div className="flex items-center gap-1 text-[11px] font-bold uppercase tracking-wider text-slate-400 shrink-0 mr-1">
            <Layers className="h-3.5 w-3.5 text-blue-400" />
            Studies ({fileList.length}):
          </div>

          {fileList.map((file, idx) => {
            const isSelected = file.id === fileId;
            const fileCat = detectFileCategory(file.originalFileName, file.mimeType);

            return (
              <button
                key={file.id}
                onClick={() => onSelectFile && onSelectFile(file.id)}
                className={`group flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs transition-all shrink-0 ${
                  isSelected
                    ? 'border-blue-500 bg-blue-600/20 text-white shadow-sm ring-1 ring-blue-500'
                    : 'border-slate-800 bg-slate-900 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                }`}
                title={file.originalFileName}
              >
                <span className={`flex h-5 w-5 items-center justify-center rounded text-[10px] font-bold ${
                  fileCat === 'dicom' ? 'bg-indigo-500/20 text-indigo-300' :
                  fileCat === 'pdf' ? 'bg-red-500/20 text-red-300' :
                  'bg-emerald-500/20 text-emerald-300'
                }`}>
                  {idx + 1}
                </span>

                <div className="text-left">
                  <p className="truncate max-w-[130px] font-medium leading-none">{file.originalFileName}</p>
                  <p className="text-[10px] text-slate-400 capitalize mt-0.5">{file.fileType.replace('_', ' ')}</p>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* 2. Top Header & Metadata HUD */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800 bg-slate-900/80 px-4 py-2.5 backdrop-blur-md">
        <div className="flex items-center gap-3 overflow-hidden">
          {/* Previous / Next Study Buttons */}
          {fileList.length > 1 && (
            <div className="flex items-center gap-0.5 border-r border-slate-800 pr-2">
              <Button
                size="sm"
                variant="ghost"
                className="h-7 w-7 p-0 text-slate-300 hover:bg-slate-800 hover:text-white"
                onClick={handlePrev}
                disabled={!hasPrev}
                title="Previous Study"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="h-7 w-7 p-0 text-slate-300 hover:bg-slate-800 hover:text-white"
                onClick={handleNext}
                disabled={!hasNext}
                title="Next Study"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          )}

          <div className="flex items-center gap-2 overflow-hidden">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-blue-600/20 text-blue-400">
              <Eye className="h-4 w-4" />
            </div>
            <div className="overflow-hidden">
              <p className="truncate text-xs font-semibold text-slate-200">{fileName}</p>
              <div className="flex items-center gap-2 text-[10px] text-slate-400">
                <span className="font-bold uppercase tracking-wider text-blue-400">
                  {category === 'dicom'
                    ? `DICOM (${renderedFile?.metadata?.modality || 'CR'})`
                    : category === 'pdf'
                    ? 'PDF Document'
                    : fileType}
                </span>
                {renderedFile?.metadata?.columns && renderedFile?.metadata?.rows && (
                  <span>&bull; {renderedFile.metadata.columns} &times; {renderedFile.metadata.rows} px</span>
                )}
                {renderedFile?.metadata?.studyDate && (
                  <span>&bull; Date: {renderedFile.metadata.studyDate}</span>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* View Transformation Metrics (HUD) */}
        {category !== 'pdf' && (
          <div className="hidden items-center gap-3 text-[11px] text-slate-400 lg:flex">
            <span>Zoom: <strong className="text-slate-200">{Math.round(zoom * 100)}%</strong></span>
            <span>B: <strong className="text-slate-200">{brightness}%</strong></span>
            <span>C: <strong className="text-slate-200">{contrast}%</strong></span>
            {inverted && <span className="rounded bg-amber-500/20 px-1.5 py-0.5 text-[10px] font-bold text-amber-400">INVERTED</span>}
          </div>
        )}

        {/* Action Controls */}
        <div className="flex items-center gap-1.5">
          {/* Split-Screen Compare Toggle (If multiple files available) */}
          {fileList.length > 1 && (
            <Button
              size="sm"
              variant={compareMode ? 'secondary' : 'ghost'}
              className={`h-8 px-2 text-xs font-medium ${
                compareMode ? 'bg-blue-600 text-white hover:bg-blue-700' : 'text-slate-300 hover:bg-slate-800'
              }`}
              onClick={() => {
                if (!compareMode) {
                  const otherFile = fileList.find((f) => f.id !== fileId);
                  if (otherFile) setCompareFileId(otherFile.id);
                }
                setCompareMode(!compareMode);
              }}
              title="Compare Studies Side-by-Side"
            >
              <Columns2 className="h-3.5 w-3.5 mr-1" />
              Compare
            </Button>
          )}

          {category !== 'pdf' && (
            <Button
              size="sm"
              variant="ghost"
              className="h-8 text-slate-300 hover:bg-slate-800 hover:text-white"
              onClick={handleReset}
              title="Reset View"
            >
              <RotateCcw className="h-3.5 w-3.5 mr-1" />
              <span className="text-xs">Reset</span>
            </Button>
          )}

          <Button
            size="sm"
            variant="ghost"
            className="h-8 w-8 p-0 text-slate-300 hover:bg-slate-800 hover:text-white"
            onClick={toggleFullscreen}
            title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen Workstation'}
          >
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>
        </div>
      </div>

      {/* 3. DICOM Window Preset Quick Bar (Bone, Soft Tissue, Lung, Brain) */}
      {category === 'dicom' && (
        <div className="flex items-center gap-1.5 border-b border-slate-800/80 bg-slate-950 px-4 py-1.5 overflow-x-auto text-xs">
          <span className="flex items-center gap-1 text-[11px] font-semibold text-slate-400 mr-2 shrink-0">
            <Sliders className="h-3.5 w-3.5 text-blue-400" />
            Window Preset:
          </span>
          {Object.entries(DICOM_PRESETS).map(([key, preset]) => (
            <button
              key={key}
              onClick={() => handlePresetSelect(key)}
              className={`rounded px-2 py-0.5 text-[11px] font-medium transition-colors shrink-0 ${
                selectedPreset === key
                  ? 'bg-blue-600 text-white font-semibold shadow-sm'
                  : 'bg-slate-900 text-slate-400 hover:bg-slate-800 hover:text-slate-200'
              }`}
            >
              {preset.label}
            </button>
          ))}
        </div>
      )}

      {/* 4. Main Diagnostic Workspace (Single or Side-by-Side Split) */}
      <div className="relative flex-1 overflow-hidden flex">
        {/* Primary View Area */}
        <div
          className="relative flex-1 cursor-grab overflow-hidden active:cursor-grabbing flex items-center justify-center bg-slate-950 select-none"
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
          onWheel={handleWheel}
        >
          {loading && (
            <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-slate-950/90">
              <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
              <p className="text-sm font-medium text-slate-300">Rendering high-resolution medical study...</p>
            </div>
          )}

          {error && (
            <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 p-6 text-center">
              <AlertCircle className="h-10 w-10 text-red-400" />
              <p className="text-sm text-red-300">{error}</p>
            </div>
          )}

          {/* Render PDF Document Mode */}
          {renderedFile?.category === 'pdf' && !loading && (
            <div className="flex h-full w-full flex-col items-center justify-center gap-4 p-6 text-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-500/10 text-blue-400 shadow-inner">
                <FileText className="h-8 w-8" />
              </div>
              <div className="space-y-1">
                <h3 className="text-base font-bold text-slate-200">PDF Clinical Lab Report</h3>
                <p className="text-xs text-slate-400 max-w-md">{fileName}</p>
              </div>
              <div className="flex items-center gap-3">
                <a
                  href={renderedFile.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-xs font-semibold text-white hover:bg-blue-700 shadow-md transition-all"
                >
                  <ExternalLink className="h-4 w-4" />
                  Open Full PDF Document
                </a>
              </div>
            </div>
          )}

          {/* Render Visual Image / DICOM Canvas */}
          {renderedFile && renderedFile.category !== 'pdf' && !loading && (
            <div
              className="flex h-full w-full items-center justify-center transition-transform duration-75 select-none"
              style={{
                transform: `translate(${position.x}px, ${position.y}px) scale(${zoom}) rotate(${rotation}deg)`,
                transformOrigin: 'center center',
              }}
            >
              <img
                src={renderedFile.url}
                alt={fileName}
                className="max-h-full max-w-full object-contain pointer-events-none"
                style={{
                  filter: `brightness(${brightness}%) contrast(${contrast}%) ${inverted ? 'invert(100%)' : ''}`,
                }}
                draggable={false}
              />
            </div>
          )}

          {/* Guidance Overlay */}
          {renderedFile?.category !== 'pdf' && !loading && (
            <div className="absolute bottom-3 left-3 pointer-events-none rounded-md bg-slate-900/80 px-2.5 py-1 text-[10px] text-slate-400 backdrop-blur-sm border border-slate-800/80">
              Scroll: Zoom &bull; Drag: Pan &bull; Presets: Windowing
            </div>
          )}
        </div>

        {/* Secondary View Area (When Compare Mode is Active) */}
        {compareMode && (
          <div className="relative flex-1 border-l border-slate-800 flex flex-col bg-slate-950">
            {/* Compare Study Selector */}
            <div className="flex items-center justify-between border-b border-slate-800 bg-slate-900/90 px-3 py-1.5 text-xs">
              <span className="font-semibold text-slate-300">Comparison Study:</span>
              <select
                className="rounded border border-slate-700 bg-slate-950 px-2 py-1 text-xs text-slate-200 focus:outline-none focus:ring-1 focus:ring-blue-500"
                value={compareFileId}
                onChange={(e) => setCompareFileId(e.target.value)}
              >
                {fileList
                  .filter((f) => f.id !== fileId)
                  .map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.originalFileName} ({f.fileType.replace('_', ' ')})
                    </option>
                  ))}
              </select>
            </div>

            {/* Compare Content */}
            <div className="relative flex-1 flex items-center justify-center overflow-hidden">
              {compareLoading && (
                <div className="absolute inset-0 flex items-center justify-center bg-slate-950/80">
                  <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
                </div>
              )}

              {compareRendered?.category === 'pdf' ? (
                <div className="flex flex-col items-center justify-center p-4 text-center gap-2">
                  <FileText className="h-8 w-8 text-blue-400" />
                  <p className="text-xs text-slate-300">PDF Report</p>
                  <a
                    href={compareRendered.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 rounded bg-blue-600 px-3 py-1 text-xs text-white"
                  >
                    <ExternalLink className="h-3 w-3" /> View
                  </a>
                </div>
              ) : compareRendered ? (
                <img
                  src={compareRendered.url}
                  alt="Comparison Study"
                  className="max-h-full max-w-full object-contain pointer-events-none"
                  draggable={false}
                />
              ) : null}
            </div>
          </div>
        )}
      </div>

      {/* 5. Bottom PACS Radiologic Control Bar */}
      {category !== 'pdf' && (
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-800 bg-slate-900/90 px-4 py-2 backdrop-blur-md">
          {/* Zoom Tools */}
          <div className="flex items-center gap-1">
            <Button
              size="sm"
              variant="ghost"
              className="h-8 w-8 p-0 text-slate-300 hover:bg-slate-800 hover:text-white"
              onClick={() => setZoom((z) => Math.max(z - 0.2, 0.4))}
              title="Zoom Out"
            >
              <ZoomOut className="h-4 w-4" />
            </Button>
            <span className="w-12 text-center text-xs font-mono text-slate-300">
              {Math.round(zoom * 100)}%
            </span>
            <Button
              size="sm"
              variant="ghost"
              className="h-8 w-8 p-0 text-slate-300 hover:bg-slate-800 hover:text-white"
              onClick={() => setZoom((z) => Math.min(z + 0.2, 8))}
              title="Zoom In"
            >
              <ZoomIn className="h-4 w-4" />
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="h-8 w-8 p-0 text-slate-300 hover:bg-slate-800 hover:text-white"
              onClick={() => setRotation((r) => (r + 90) % 360)}
              title="Rotate Clockwise"
            >
              <RotateCw className="h-4 w-4" />
            </Button>
          </div>

          {/* Radiologic Windowing / Sliders */}
          <div className="flex items-center gap-4">
            {/* Brightness */}
            <div className="flex items-center gap-1.5" title="Brightness">
              <Sun className="h-3.5 w-3.5 text-slate-400" />
              <input
                type="range"
                min="30"
                max="250"
                value={brightness}
                onChange={(e) => setBrightness(Number(e.target.value))}
                className="h-1.5 w-16 accent-blue-500 bg-slate-700 rounded-lg cursor-pointer"
              />
            </div>

            {/* Contrast */}
            <div className="flex items-center gap-1.5" title="Contrast">
              <Contrast className="h-3.5 w-3.5 text-slate-400" />
              <input
                type="range"
                min="30"
                max="250"
                value={contrast}
                onChange={(e) => setContrast(Number(e.target.value))}
                className="h-1.5 w-16 accent-blue-500 bg-slate-700 rounded-lg cursor-pointer"
              />
            </div>

            {/* Invert Button */}
            <Button
              size="sm"
              variant={inverted ? 'secondary' : 'ghost'}
              className={`h-7 px-2 text-xs font-semibold ${
                inverted
                  ? 'bg-blue-600 text-white hover:bg-blue-700'
                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
              }`}
              onClick={() => setInverted((inv) => !inv)}
              title="Invert Colors (Bone & Lung Consolidation)"
            >
              <Sparkles className="mr-1 h-3 w-3" />
              Invert
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
