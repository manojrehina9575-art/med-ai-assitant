import { useEffect, useRef, useState } from 'react';
import {
  ExternalLink,
  ImageOff,
  Loader2,
  LocateFixed,
  MousePointerClick,
  Maximize2,
  Minimize2,
  RotateCcw,
  X,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { AnatomyViewer, type AnatomyViewerHandle } from '@/components/anatomy-viewer/AnatomyViewer';
import { describeMeshName, type ExploredStructure } from '@/components/anatomy-viewer/utils/anatomyMeshDescription';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { fileService } from '@/services/fileService';
import { cn } from '@/utils/cn';
import type { AnatomySelection } from '@/types/clinicalWorkspace';
import type { MedicalFile } from '@/types';

const SIDE_LABEL: Record<ExploredStructure['side'], string> = {
  LEFT: 'Left',
  RIGHT: 'Right',
  MIDLINE: 'Midline',
};

interface AnatomyPreviewProps {
  selection: AnatomySelection | null;
  linkedIssueType?: string | null;
  /** Set when the linked issue maps to more than one structure. */
  conflictNote?: string | null;
  /** Real patient id, used to load the underlying 2D study image. Null/absent in demo mode. */
  patientId?: string | null;
}

type ViewMode = '3D' | '2D';

const IMAGE_FILE_TYPES = new Set(['XRAY', 'CT_SCAN', 'ULTRASOUND', 'MRI']);

export function AnatomyPreview({ selection, linkedIssueType, conflictNote, patientId = null }: AnatomyPreviewProps) {
  // Machine identifier, not clinical content: useful while wiring the viewer, noise for reviewers.
  const showViewerKey = import.meta.env.DEV && Boolean(selection?.viewerKey);

  const [viewMode, setViewMode] = useState<ViewMode>('3D');
  const [studyImage, setStudyImage] = useState<MedicalFile | null>(null);
  const [studyImageLoading, setStudyImageLoading] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [exploredStructure, setExploredStructure] = useState<ExploredStructure | null>(null);
  const [infoPanelOpen, setInfoPanelOpen] = useState(true);
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const viewerContainerRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<AnatomyViewerHandle>(null);

  // A clicked bone is exploration only, never a verified finding — it never overwrites `selection`.
  // Clearing it (Reset View, or a new mapped selection arriving) reverts the card to `selection`.
  useEffect(() => {
    setExploredStructure(null);
    setInfoPanelOpen(true);
  }, [selection]);

  useEffect(() => {
    setThumbnailFailed(false);
  }, [patientId, studyImage?.id]);

  useEffect(() => {
    setStudyImage(null);
    if (!patientId) return;

    let cancelled = false;
    setStudyImageLoading(true);

    fileService
      .list(patientId, 0, 10)
      .then((page) => {
        if (cancelled) return;
        setStudyImage(page.content.find((file) => IMAGE_FILE_TYPES.has(file.fileType)) ?? null);
      })
      .catch(() => {
        if (!cancelled) setStudyImage(null);
      })
      .finally(() => {
        if (!cancelled) setStudyImageLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [patientId]);

  useEffect(() => {
    const handler = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', handler);
    return () => document.removeEventListener('fullscreenchange', handler);
  }, []);

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
      return;
    }
    void viewerContainerRef.current?.requestFullscreen();
  }

  function handleExploredMeshChange(meshName: string | null) {
    setExploredStructure(meshName ? describeMeshName(meshName) : null);
    if (meshName) setInfoPanelOpen(true);
  }

  const thumbnail = patientId && studyImage && !thumbnailFailed
    ? { patientId, file: studyImage }
    : null;

  return (
    <Card className={cn('h-full min-w-0 overflow-hidden', isFullscreen && 'bg-slate-950')}>
      <CardHeader className="border-b" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <CardTitle className="flex items-center gap-2">
              <LocateFixed className="h-4 w-4 text-violet-400" />
              Anatomy Viewer
            </CardTitle>
            <CardDescription>Selected structure context</CardDescription>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            {linkedIssueType && <span className="badge badge-purple text-[10px]">QA linked</span>}

            <button
              type="button"
              onClick={() => viewerRef.current?.resetView()}
              disabled={viewMode !== '3D' || !selection}
              title="Reset View"
              className="flex h-7 w-7 items-center justify-center rounded-lg border text-slate-400 transition-colors hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
              style={{ borderColor: 'var(--clr-border-2, #243250)' }}
            >
              <RotateCcw className="h-3.5 w-3.5" />
            </button>

            <div className="flex items-center gap-0.5 rounded-lg border p-0.5" style={{ borderColor: 'var(--clr-border-2, #243250)' }}>
              <ViewModeButton active={viewMode === '3D'} onClick={() => setViewMode('3D')}>3D</ViewModeButton>
              <ViewModeButton active={viewMode === '2D'} onClick={() => setViewMode('2D')}>2D</ViewModeButton>
            </div>

            <button
              type="button"
              onClick={toggleFullscreen}
              title={isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
              className="flex h-7 w-7 items-center justify-center rounded-lg border text-slate-400 transition-colors hover:text-white"
              style={{ borderColor: 'var(--clr-border-2, #243250)' }}
            >
              {isFullscreen ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
            </button>
          </div>
        </div>
      </CardHeader>

      <CardContent className={cn('space-y-4 p-4 xl:p-5', isFullscreen && 'flex min-h-screen flex-col')}>
        {selection ? (
          <div
            className={cn(
              'grid gap-4 xl:grid-cols-[minmax(0,1fr)_18rem] 2xl:grid-cols-[minmax(0,1fr)_20rem]',
              isFullscreen && 'min-h-0 flex-1 xl:grid-cols-[minmax(0,1fr)_22rem]'
            )}
          >
            <div
              ref={viewerContainerRef}
              className={cn('min-w-0', isFullscreen && 'flex min-h-0 flex-col justify-center p-6')}
              style={isFullscreen ? { background: 'var(--bg, #0a0f1e)' } : undefined}
            >
              {viewMode === '3D' ? (
                /* 3D view of the mapped structure. Driven by the backend viewerKey only. */
                <AnatomyViewer
                  ref={viewerRef}
                  selection={selection}
                  size={isFullscreen ? 'fullscreen' : 'workspace'}
                  onExploredMeshChange={handleExploredMeshChange}
                />
              ) : (
                <TwoDImagePane
                  patientId={patientId}
                  loading={studyImageLoading}
                  file={studyImage}
                  fullscreen={isFullscreen}
                />
              )}
            </div>

            {infoPanelOpen ? (
              <div
                className="min-w-0 space-y-3 rounded-xl border px-4 py-3 shadow-[0_18px_50px_rgba(0,0,0,0.18)]"
                style={{
                  borderColor: exploredStructure ? 'rgba(96,165,250,0.4)' : 'var(--clr-border, #1e2d45)',
                  background: 'var(--surface-2, #1a2235)',
                }}
              >
                <div className="flex items-start justify-between gap-2">
                  <p
                    className={cn(
                      'flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest',
                      exploredStructure ? 'text-blue-300' : 'text-slate-500'
                    )}
                  >
                    {exploredStructure && <MousePointerClick className="h-3 w-3" />}
                    {exploredStructure ? 'Clicked in 3D View' : 'Selected Anatomy'}
                  </p>
                  <button
                    type="button"
                    onClick={() => setInfoPanelOpen(false)}
                    title="Hide panel"
                    className="shrink-0 rounded p-0.5 text-slate-500 hover:text-white"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>

                {thumbnail && (
                  <button
                    type="button"
                    onClick={() => setViewMode('2D')}
                    title="View the study image"
                    className="block h-32 w-full overflow-hidden rounded-lg border"
                    style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface, #111827)' }}
                  >
                    <img
                      src={fileService.getViewUrl(thumbnail.patientId, thumbnail.file.id)}
                      alt={thumbnail.file.originalFileName ?? 'Study image'}
                      className="h-full w-full object-cover"
                      onError={() => setThumbnailFailed(true)}
                    />
                  </button>
                )}

                {exploredStructure ? (
                  <>
                    <div
                      className="rounded-lg border px-3 py-3"
                      style={{ borderColor: 'rgba(96,165,250,0.25)', background: 'rgba(37,99,235,0.08)' }}
                    >
                      <p className="text-[10px] font-bold uppercase tracking-widest text-blue-300">
                        3D Selection
                      </p>
                      <p className="mt-1 text-base font-semibold leading-6 text-white">
                        {exploredStructure.displayName}
                      </p>
                    </div>

                    <dl className="divide-y" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
                      <InfoRow label="Structure" value={exploredStructure.displayName} />
                      <InfoRow label="Side" value={SIDE_LABEL[exploredStructure.side]} />
                      <InfoRow label="System" value={exploredStructure.system} />
                      <InfoRow label="Role" value={exploredStructure.role} />
                      <InfoRow label="Mesh" value={exploredStructure.meshName} mono />
                      {exploredStructure.fma && <InfoRow label="FMA" value={exploredStructure.fma} mono />}
                      {exploredStructure.representation && (
                        <InfoRow label="BP" value={exploredStructure.representation} mono />
                      )}
                      {exploredStructure.source && <InfoRow label="Source" value={exploredStructure.source} />}
                      {typeof exploredStructure.elementCount === 'number' && (
                        <InfoRow label="Elements" value={String(exploredStructure.elementCount)} />
                      )}
                    </dl>

                    <p className="text-[11px] leading-5 text-amber-200/80">
                      Visual inspection only. The mapped report anatomy remains {selection.displayName}.
                    </p>
                  </>
                ) : (
                  <>
                    <div
                      className="rounded-lg border px-3 py-3"
                      style={{ borderColor: 'rgba(139,92,246,0.25)', background: 'rgba(88,28,135,0.12)' }}
                    >
                      <p className="text-[10px] font-bold uppercase tracking-widest text-violet-300">
                        Mapped Anatomy
                      </p>
                      <p className="mt-1 text-base font-semibold leading-6 text-white">{selection.displayName}</p>
                      {selection.sourceLabel && (
                        <p className="mt-1 text-[11px] text-slate-400">
                          Source finding: {selection.sourceLabel}
                        </p>
                      )}
                      {selection.comparisonLabel && (
                        <p className="mt-0.5 text-[11px] text-slate-400">
                          Comparison: {selection.comparisonLabel}
                        </p>
                      )}
                    </div>

                    <dl className="divide-y" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
                      <InfoRow label="Structure" value={selection.structure} />
                      <InfoRow label="Side" value={selection.side} />
                      <InfoRow label="Region" value={selection.region} />
                      <InfoRow label="System" value={selection.system} />
                    </dl>

                    {selection.sourceText && (
                      <div
                        className="rounded-lg border px-3 py-2"
                        style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface, #111827)' }}
                      >
                        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Source Finding Text</p>
                        <p className="mt-1 text-xs leading-5 text-slate-300">"{selection.sourceText}"</p>
                      </div>
                    )}

                    {showViewerKey && (
                      <p className="font-mono text-[10px] text-slate-500">viewerKey: {selection.viewerKey}</p>
                    )}
                  </>
                )}
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setInfoPanelOpen(true)}
                className="h-9 rounded-lg border px-3 text-xs font-semibold text-slate-400 hover:text-white xl:h-full xl:w-9 xl:px-0"
                style={{ borderColor: 'var(--clr-border-2, #243250)' }}
                title="Show anatomy details"
              >
                Show Details
              </button>
            )}
          </div>
        ) : (
          <div
            className="rounded-lg border px-4 py-8 text-center"
            style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface-2, #1a2235)' }}
          >
            <p className="text-sm font-semibold text-white">No anatomy selected</p>
            <p className="mt-1 text-xs leading-5 text-slate-400">
              Run QA or choose a mapped finding to view anatomy context.
            </p>
          </div>
        )}

        {conflictNote && (
          <div className="rounded-lg border border-amber-500/25 bg-amber-950/20 px-3 py-2">
            <p className="text-[11px] font-semibold text-amber-200">Conflicting mapped structures</p>
            <p className="mt-0.5 text-[11px] leading-5 text-amber-100/80">{conflictNote}</p>
          </div>
        )}

        {linkedIssueType && (
          <div className="rounded-lg border border-violet-500/20 bg-violet-950/20 px-3 py-2">
            <p className="text-[11px] font-semibold text-violet-200">{linkedIssueType}</p>
            <p className="mt-0.5 text-[11px] text-violet-100/70">
              Mapped from the source finding for this QA issue. Laterality is not verified by the system.
            </p>
          </div>
        )}

        <Link
          to="/anatomy"
          className="inline-flex h-9 w-full items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold text-white transition-all"
          style={{ background: 'linear-gradient(135deg, #3b82f6, #2563eb)' }}
        >
          Open Anatomy
          <ExternalLink className="h-3.5 w-3.5" />
        </Link>
      </CardContent>
    </Card>
  );
}

function ViewModeButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'rounded-md px-2.5 py-1 text-[11px] font-bold transition-colors',
        active ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
      )}
    >
      {children}
    </button>
  );
}

function TwoDImagePane({
  patientId,
  loading,
  file,
  fullscreen,
}: {
  patientId: string | null;
  loading: boolean;
  file: MedicalFile | null;
  fullscreen: boolean;
}) {
  const [imageFailed, setImageFailed] = useState(false);
  const heightClass = fullscreen
    ? 'h-[calc(100vh-13rem)] min-h-[34rem]'
    : 'h-[27rem] xl:h-[32rem] 2xl:h-[35rem]';

  useEffect(() => {
    setImageFailed(false);
  }, [patientId, file?.id]);

  if (loading) {
    return (
      <div
        className={cn('flex items-center justify-center rounded-xl border', heightClass)}
        style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
      >
        <Loader2 className="h-5 w-5 animate-spin text-slate-500" />
      </div>
    );
  }

  if (!patientId || !file || imageFailed) {
    return (
      <div
        className={cn('flex flex-col items-center justify-center gap-2 rounded-xl border text-center', heightClass)}
        style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
      >
        <ImageOff className="h-5 w-5 text-slate-600" />
        <p className="text-xs text-slate-500">No 2D study image available for this patient yet.</p>
      </div>
    );
  }

  return (
    <div
      className={cn('flex items-center justify-center overflow-hidden rounded-xl border', heightClass)}
      style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
    >
      <img
        src={fileService.getViewUrl(patientId, file.id)}
        alt={file.originalFileName ?? 'Study image'}
        className="h-full w-full object-contain"
        onError={() => setImageFailed(true)}
      />
    </div>
  );
}

function InfoRow({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-3 py-1.5 text-xs first:pt-0 last:pb-0">
      <dt className="shrink-0 text-slate-500">{label}</dt>
      <dd className={cn('min-w-0 text-right font-semibold text-slate-200', mono && 'font-mono text-[11px]')}>
        {value}
      </dd>
    </div>
  );
}
