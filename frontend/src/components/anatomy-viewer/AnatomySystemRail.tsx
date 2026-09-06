import { Bone, Focus, PersonStanding, Salad, Waypoints } from 'lucide-react';
import { cn } from '@/utils/cn';

interface AnatomySystemRailProps {
  isolated: boolean;
  canIsolate: boolean;
  onWholeBody: () => void;
  onIsolate: () => void;
}

/**
 * The viewer renders the skeletal system plus brain, cranial nerve context, lungs, and kidneys.
 * Muscular and a general Organs system view are shown but disabled with an honest reason, rather
 * than hidden, so it's clear they're a known gap and not broken.
 */
export function AnatomySystemRail({ isolated, canIsolate, onWholeBody, onIsolate }: AnatomySystemRailProps) {
  return (
    <div className="flex shrink-0 flex-col gap-1.5">
      <RailButton icon={PersonStanding} label="Whole Body" active={!isolated} onClick={onWholeBody} />
      <RailButton
        icon={Bone}
        label="Skeleton"
        active={false}
        onClick={onWholeBody}
        title="This viewer renders the skeletal system."
      />
      <RailButton icon={Waypoints} label="Muscular" active={false} disabled title="Not available — no muscular system data in this viewer yet." />
      <RailButton icon={Salad} label="Organs" active={false} disabled title="Brain with cranial nerve context, lungs, and kidneys are supported when mapped from a finding, but there's no general organ-system view yet." />
      <RailButton
        icon={Focus}
        label="Isolate"
        active={isolated}
        onClick={onIsolate}
        disabled={!canIsolate}
        title={canIsolate ? 'Focus on the selected structure' : 'No single structure to isolate'}
      />
    </div>
  );
}

function RailButton({
  icon: Icon,
  label,
  active,
  onClick,
  disabled = false,
  title,
}: {
  icon: typeof Bone;
  label: string;
  active: boolean;
  onClick?: () => void;
  disabled?: boolean;
  title?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={cn(
        'flex w-16 flex-col items-center gap-1 rounded-lg border px-2 py-2 text-[10px] font-medium transition-colors',
        disabled
          ? 'cursor-not-allowed border-transparent opacity-40'
          : active
          ? 'border-blue-500/50 bg-blue-500/15 text-blue-200'
          : 'border-transparent text-slate-400 hover:bg-white/5 hover:text-white'
      )}
    >
      <Icon className="h-4 w-4" />
      {label}
    </button>
  );
}
