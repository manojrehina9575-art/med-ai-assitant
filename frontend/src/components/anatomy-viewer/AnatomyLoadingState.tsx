import { Loader2 } from 'lucide-react';

/** Shown while the viewer chunk or the model is loading. Never a blank panel. */
export function AnatomyLoadingState({ message = 'Loading 3D anatomy view…' }: { message?: string }) {
  return (
    <div
      className="flex h-full min-h-56 items-center justify-center rounded-lg border border-dashed"
      style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
    >
      <div className="text-center">
        <Loader2 className="mx-auto mb-2 h-6 w-6 animate-spin text-blue-300" />
        <p className="text-sm font-semibold text-white">{message}</p>
        <p className="mt-1 text-xs text-slate-500">Report text is not affected.</p>
      </div>
    </div>
  );
}
