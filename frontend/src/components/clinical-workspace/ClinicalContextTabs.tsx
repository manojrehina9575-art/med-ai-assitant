import { LongitudinalComparisonPanel } from '@/components/clinical-workspace/LongitudinalComparisonPanel';
import { Card, CardContent } from '@/components/ui/Card';
import type { ReportReview } from '@/services/reportService';
import type {
  AnatomySelection,
  AuditEvent,
  ClinicalContextTab,
  PriorStudy,
  TimelineEvent,
} from '@/types/clinicalWorkspace';

interface ClinicalContextTabsProps {
  activeTab: ClinicalContextTab;
  priorStudies: PriorStudy[];
  timeline: TimelineEvent[];
  audit: AuditEvent[];
  isDemoMode: boolean;
  currentReview: ReportReview | null;
  /** Forwarded to the longitudinal panel so comparisons can drive the shared anatomy preview. */
  onViewAnatomy?: (selection: AnatomySelection) => void;
}

/**
 * Full-width content for the non-"Clinical Workspace" tabs in `WorkspaceTabBar`. The tab bar itself
 * lives at the page level now, directly under the patient banner, matching the workspace mockup.
 */
export function ClinicalContextTabs({
  activeTab,
  priorStudies,
  timeline,
  audit,
  isDemoMode,
  currentReview,
  onViewAnatomy,
}: ClinicalContextTabsProps) {
  return (
    <Card>
      <div className="border-b px-4 py-3" style={{ borderColor: 'var(--clr-border, #1e2d45)' }}>
        <p className="text-xs text-slate-500">
          {isDemoMode ? 'Demo supporting data for workspace layout' : 'Patient context and report comparison'}
        </p>
      </div>

      <CardContent className="p-0">
        {activeTab === 'prior-studies' && (
          isDemoMode ? (
            <PriorStudiesView priorStudies={priorStudies} />
          ) : (
            <LongitudinalComparisonPanel currentReview={currentReview} onViewAnatomy={onViewAnatomy} />
          )
        )}
        {activeTab === 'timeline' && <TimelineView timeline={timeline} />}
        {activeTab === 'audit' && <AuditView audit={audit} />}
      </CardContent>
    </Card>
  );
}

function PriorStudiesView({ priorStudies }: { priorStudies: PriorStudy[] }) {
  return (
    <div className="divide-y divide-slate-800/80">
      {priorStudies.map((study) => (
        <div key={study.id} className="grid gap-2 px-4 py-3 md:grid-cols-[9rem_12rem_1fr] md:items-center">
          <div className="text-xs font-semibold text-slate-300">{study.date}</div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs font-medium text-white">{study.studyType}</span>
            <span className="badge badge-slate text-[10px]">{study.modality}</span>
          </div>
          <p className="text-xs leading-5 text-slate-400">{study.summary}</p>
        </div>
      ))}
    </div>
  );
}

function TimelineView({ timeline }: { timeline: TimelineEvent[] }) {
  return (
    <div className="p-4">
      <div className="space-y-3">
        {timeline.map((event) => (
          <div key={event.id} className="flex gap-3">
            <div className="w-12 shrink-0 pt-0.5 font-mono text-xs text-slate-500">{event.time}</div>
            <div className="flex-1 rounded-lg border px-3 py-2" style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface-2, #1a2235)' }}>
              <p className="text-sm font-medium text-white">{event.label}</p>
              {event.detail && <p className="mt-0.5 text-xs text-slate-500">{event.detail}</p>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function AuditView({ audit }: { audit: AuditEvent[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left text-xs">
        <thead className="border-b border-slate-800 bg-slate-950/50 text-slate-500">
          <tr>
            <th className="px-4 py-3 font-semibold uppercase tracking-wider">Event</th>
            <th className="px-4 py-3 font-semibold uppercase tracking-wider">Actor</th>
            <th className="px-4 py-3 font-semibold uppercase tracking-wider">Time</th>
            <th className="px-4 py-3 font-semibold uppercase tracking-wider">Detail</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/80 text-slate-300">
          {audit.map((event) => (
            <tr key={event.id}>
              <td className="px-4 py-3 font-medium text-white">{event.label}</td>
              <td className="px-4 py-3">{event.actor ?? 'System'}</td>
              <td className="px-4 py-3 font-mono">{event.time}</td>
              <td className="px-4 py-3 text-slate-400">{event.detail ?? 'Demo event'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
