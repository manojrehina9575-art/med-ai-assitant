interface AnatomyCompassProps {
  /** Camera azimuth in degrees, 0 at the default front-facing view. */
  azimuthDegrees: number;
}

/**
 * Orientation widget for the 3D view: Superior/Inferior stay fixed top/bottom (the camera never
 * rolls), while Right/Left rotate around the ring to track the camera's actual azimuth — so it
 * keeps pointing at the true anatomical side as the reviewer orbits, rather than a static label
 * that would go wrong the moment the camera moves.
 */
export function AnatomyCompass({ azimuthDegrees }: AnatomyCompassProps) {
  return (
    <div className="pointer-events-none absolute bottom-3 left-3 h-16 w-16">
      <div className="relative h-full w-full rounded-full border border-slate-600/50 bg-slate-950/70 backdrop-blur-sm">
        <div className="absolute left-1/2 top-1/2 h-1 w-1 -translate-x-1/2 -translate-y-1/2 rounded-full bg-slate-400" />
        <CompassLabel angleDegrees={0} text="S" />
        <CompassLabel angleDegrees={180} text="I" />
        <CompassLabel angleDegrees={270 - azimuthDegrees} text="R" tone="text-blue-300" />
        <CompassLabel angleDegrees={90 - azimuthDegrees} text="L" tone="text-blue-300" />
      </div>
    </div>
  );
}

function CompassLabel({ angleDegrees, text, tone = 'text-slate-300' }: { angleDegrees: number; text: string; tone?: string }) {
  const radians = (((angleDegrees % 360) + 360) % 360) * (Math.PI / 180);
  const radius = 40; // percent of the widget's half-size, leaving room for the label itself
  const left = 50 + radius * Math.sin(radians) * 0.5;
  const top = 50 - radius * Math.cos(radians) * 0.5;

  return (
    <span
      className={`absolute -translate-x-1/2 -translate-y-1/2 text-[10px] font-bold ${tone}`}
      style={{ left: `${left}%`, top: `${top}%` }}
    >
      {text}
    </span>
  );
}
