import { Suspense, forwardRef, lazy, useEffect, useImperativeHandle, useMemo, useState } from 'react';
import { AnatomyAttribution } from './AnatomyAttribution';
import { AnatomyControls, type AnatomyInteractionMode } from './AnatomyControls';
import { AnatomyErrorBoundary } from './AnatomyErrorBoundary';
import { AnatomyLoadingState } from './AnatomyLoadingState';
import { AnatomySystemRail } from './AnatomySystemRail';
import { AnatomyViewerFallback } from './AnatomyViewerFallback';
import { skeletonModelUrl } from './model/anatomyViewerManifest';
import { resolveViewerTarget } from './utils/anatomyMeshResolver';
import {
  initialAnatomyViewState,
  reduceAnatomyViewState,
  type AnatomyViewAction,
} from './utils/anatomyViewerState';
import { supportsWebGl } from './utils/webgl';
import { cn } from '@/utils/cn';
import type { AnatomySelection } from '@/types/clinicalWorkspace';

// three.js and the WebGL scene load on demand, keeping them out of the initial workspace bundle.
const SkeletonScene = lazy(() => import('./SkeletonScene'));

interface AnatomyViewerProps {
  /**
   * The mapped structure to show. Only its `viewerKey` selects meshes — `displayName` and the other
   * labels are presentation and are never parsed.
   */
  selection: AnatomySelection;
  /**
   * Notified whenever a raw click-to-explore mesh changes (a name when a bone is clicked, `null` on
   * reset or when the mapped selection changes). Lets the surrounding card mirror the click without
   * this component owning any clinical-selection state.
   */
  onExploredMeshChange?: (meshName: string | null) => void;
  size?: 'workspace' | 'fullscreen';
}

/** Imperative handle so a header-level control outside this component can trigger Reset View. */
export interface AnatomyViewerHandle {
  resetView: () => void;
}

/**
 * Skeleton Viewer V1.
 *
 * Consumes the backend anatomy target's `viewerKey`, resolves it through the manifest, and renders
 * the skeleton with that structure highlighted. The camera rests on the whole-body view by default;
 * "Focus on Structure" (rail or controls) is what zooms it in. Anything it cannot render is
 * reported honestly; the panel's text metadata is always shown by the surrounding card.
 */
export const AnatomyViewer = forwardRef<AnatomyViewerHandle, AnatomyViewerProps>(function AnatomyViewer(
  { selection, onExploredMeshChange, size = 'workspace' },
  ref
) {
  const target = useMemo(() => resolveViewerTarget(selection.viewerKey), [selection.viewerKey]);
  const [viewState, setViewState] = useState(initialAnatomyViewState);
  const [interactionMode, setInteractionMode] = useState<AnatomyInteractionMode>('move');
  const [exploredMeshName, setExploredMeshNameState] = useState<string | null>(null);

  function setExploredMeshName(meshName: string | null) {
    setExploredMeshNameState(meshName);
    onExploredMeshChange?.(meshName);
  }
  const webGlAvailable = useMemo(supportsWebGl, []);

  function dispatch(action: AnatomyViewAction) {
    setViewState((state) => reduceAnatomyViewState(state, action, { canIsolate: target.supported }));
  }

  function resetView() {
    setExploredMeshName(null);
    dispatch({ type: 'RESET_VIEW' });
  }

  useImperativeHandle(ref, () => ({ resetView }));

  // A new clinical selection drops viewer-local exploration and any isolate that no longer applies.
  useEffect(() => {
    setExploredMeshName(null);
    setViewState((state) =>
      reduceAnatomyViewState(state, { type: 'SELECTION_CHANGED' }, { canIsolate: target.supported })
    );
  }, [target.supported, target.viewerKey]);

  const highlightedMeshNames = exploredMeshName
    ? [exploredMeshName]
    : target.supported
    ? target.meshNames
    : [];
  const sceneHeightClass =
    size === 'fullscreen'
      ? 'h-[calc(100vh-13rem)] min-h-[34rem]'
      : 'h-[27rem] xl:h-[32rem] 2xl:h-[35rem]';

  if (!target.supported) {
    return (
      <div className="space-y-3">
        <AnatomyViewerFallback
          title={
            target.reason === 'NO_VIEWER_KEY'
              ? 'No single 3D structure'
              : '3D model not available yet'
          }
          message={target.message}
          detail={
            target.reason === 'NON_SKELETAL_SYSTEM'
              ? 'Skeleton Viewer V1 renders the skeletal system plus brain with cranial nerve context, lungs, and kidneys. Mapped structure details remain below.'
              : target.reason === 'NO_VIEWER_KEY'
              ? 'Bilateral and unspecified findings have no single structure to display.'
              : null
          }
        />
        <AnatomyAttribution />
      </div>
    );
  }

  if (!webGlAvailable) {
    return (
      <div className="space-y-3">
        <AnatomyViewerFallback
          title="3D view unavailable"
          message="This browser or environment cannot display the 3D anatomy view."
          detail={`Mapped structures: ${target.coveredStructures.join(', ')}.`}
        />
        <AnatomyAttribution />
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-3">
        <AnatomySystemRail
          isolated={viewState.isolated}
          canIsolate={target.supported}
          onWholeBody={resetView}
          onIsolate={() => dispatch({ type: 'ISOLATE_STRUCTURE' })}
        />

        <div
          className={cn('flex-1 overflow-hidden rounded-xl border shadow-[inset_0_0_0_1px_rgba(255,255,255,0.02)]', sceneHeightClass)}
          style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
        >
          <AnatomyErrorBoundary
            resetKey={target.viewerKey}
            fallback={
              <AnatomyViewerFallback
                tone="error"
                title="3D anatomy model could not be loaded"
                message="Review can continue from the mapped structure details below."
              />
            }
          >
            <Suspense fallback={<AnatomyLoadingState />}>
              <SkeletonScene
                highlightedMeshNames={highlightedMeshNames}
                selectionKey={target.viewerKey}
                focusRegion={target.focusRegion}
                isolated={viewState.isolated}
                resetNonce={viewState.resetNonce}
                focusNonce={viewState.focusNonce}
                interactionMode={interactionMode}
                onExploreMesh={setExploredMeshName}
              />
            </Suspense>
          </AnatomyErrorBoundary>
        </div>
      </div>

      <AnatomyControls
        canIsolate={target.supported}
        isolated={viewState.isolated}
        interactionMode={interactionMode}
        onInteractionModeChange={setInteractionMode}
        onResetView={resetView}
        onIsolate={() => dispatch({ type: 'ISOLATE_STRUCTURE' })}
        onShowFullSkeleton={() => dispatch({ type: 'SHOW_FULL_SKELETON' })}
      />

      <p className="text-[10px] leading-4 text-slate-500">
        Highlighted structures: {target.coveredStructures.join(', ')}. Move drags the anatomy.
      </p>

      {exploredMeshName && (
        <p className="text-[11px] leading-5 text-amber-200/80">
          Viewing {exploredMeshName} selected in the 3D view. This is visual exploration only and is
          not a clinical finding. Use Reset View to return to the mapped structure.
        </p>
      )}

      {!skeletonModelUrl && (
        <p className="text-[11px] leading-5 text-slate-400">
          Schematic placeholder skeleton. Structure identity, highlighting and focus follow the
          mapped anatomy target; proportions are not anatomically accurate.
        </p>
      )}

      <AnatomyAttribution />
    </div>
  );
});
