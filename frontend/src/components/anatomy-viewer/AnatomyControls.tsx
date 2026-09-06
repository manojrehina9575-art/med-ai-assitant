import { Eye, Focus, Move3D, Rotate3D, RotateCcw } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export type AnatomyInteractionMode = 'move' | 'rotate';

interface AnatomyControlsProps {
  canIsolate: boolean;
  isolated: boolean;
  interactionMode: AnatomyInteractionMode;
  onInteractionModeChange: (mode: AnatomyInteractionMode) => void;
  onResetView: () => void;
  onIsolate: () => void;
  onShowFullSkeleton: () => void;
}

/** Viewer-only actions. None of these change the clinical anatomy selection. */
export function AnatomyControls({
  canIsolate,
  isolated,
  interactionMode,
  onInteractionModeChange,
  onResetView,
  onIsolate,
  onShowFullSkeleton,
}: AnatomyControlsProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div
        className="flex items-center gap-1 rounded-lg border p-1"
        style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'rgba(15,23,42,0.5)' }}
      >
        <Button
          type="button"
          size="sm"
          variant={interactionMode === 'move' ? 'default' : 'ghost'}
          aria-pressed={interactionMode === 'move'}
          onClick={() => onInteractionModeChange('move')}
        >
          <Move3D className="h-3.5 w-3.5" />
          Move
        </Button>
        <Button
          type="button"
          size="sm"
          variant={interactionMode === 'rotate' ? 'default' : 'ghost'}
          aria-pressed={interactionMode === 'rotate'}
          onClick={() => onInteractionModeChange('rotate')}
        >
          <Rotate3D className="h-3.5 w-3.5" />
          Rotate
        </Button>
      </div>
      <Button type="button" size="sm" variant="outline" onClick={onResetView}>
        <RotateCcw className="h-3.5 w-3.5" />
        Reset View
      </Button>
      {isolated ? (
        <Button type="button" size="sm" variant="outline" onClick={onShowFullSkeleton}>
          <Eye className="h-3.5 w-3.5" />
          Show Full Skeleton
        </Button>
      ) : (
        <Button type="button" size="sm" variant="outline" onClick={onIsolate} disabled={!canIsolate}>
          <Focus className="h-3.5 w-3.5" />
          Focus on Structure
        </Button>
      )}
    </div>
  );
}
