import { Box, TriangleAlert } from 'lucide-react';

export type AnatomyFallbackTone = 'error' | 'unsupported';

interface AnatomyViewerFallbackProps {
  title: string;
  message: string;
  tone?: AnatomyFallbackTone;
  /** Optional secondary line, e.g. the structures the target covers. */
  detail?: string | null;
}

/**
 * Safe stand-in for the 3D surface.
 *
 * Used for load failures, environments without WebGL, and anatomy the Skeleton Viewer V1 genuinely
 * cannot render. It never draws a substitute structure, and the panel's text metadata always stays
 * visible alongside it.
 */
export function AnatomyViewerFallback({ title, message, tone = 'unsupported', detail }: AnatomyViewerFallbackProps) {
  const isError = tone === 'error';

  return (
    <div
      role="status"
      className="flex h-full min-h-56 items-center justify-center rounded-lg border border-dashed px-4 py-6"
      style={{
        borderColor: isError ? 'rgba(248,113,113,0.35)' : 'var(--clr-border-2, #243250)',
        background: isError ? 'rgba(69,10,10,0.25)' : 'var(--surface-2, #1a2235)',
      }}
    >
      <div className="text-center">
        <div
          className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl border"
          style={{
            borderColor: isError ? 'rgba(248,113,113,0.3)' : 'rgba(139,92,246,0.25)',
            background: isError ? 'rgba(248,113,113,0.1)' : 'rgba(139,92,246,0.1)',
          }}
        >
          {isError ? (
            <TriangleAlert className="h-6 w-6 text-red-300" />
          ) : (
            <Box className="h-6 w-6 text-violet-300" />
          )}
        </div>
        <p className={`text-sm font-semibold ${isError ? 'text-red-100' : 'text-white'}`}>{title}</p>
        <p className={`mt-1 text-xs leading-5 ${isError ? 'text-red-200/80' : 'text-slate-400'}`}>{message}</p>
        {detail && <p className="mt-1 text-[11px] leading-5 text-slate-500">{detail}</p>}
      </div>
    </div>
  );
}
