import { useEffect, useState } from 'react';
import { ClipboardList, FileClock, NotebookPen } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/Card';
import { patientService } from '@/services/patientService';
import { reportService } from '@/services/reportService';

interface ClinicalContextSidebarProps {
  /** Real patient id to load context for. Null in demo mode. */
  patientId: string | null;
  onViewPriorReports: () => void;
  onAddNote: () => void;
}

export function ClinicalContextSidebar({ patientId, onViewPriorReports, onAddNote }: ClinicalContextSidebarProps) {
  const [priorReportCount, setPriorReportCount] = useState<number | null>(null);
  const [medicalHistory, setMedicalHistory] = useState<string[] | null>(null);

  useEffect(() => {
    if (!patientId) {
      setPriorReportCount(null);
      setMedicalHistory(null);
      return;
    }

    let cancelled = false;

    reportService
      .forPatient(patientId, 0, 1)
      .then((page) => {
        if (!cancelled) setPriorReportCount(page.totalElements);
      })
      .catch(() => {
        if (!cancelled) setPriorReportCount(null);
      });

    patientService
      .get(patientId)
      .then((patient) => {
        if (!cancelled) setMedicalHistory(patient.medicalHistory ?? []);
      })
      .catch(() => {
        if (!cancelled) setMedicalHistory(null);
      });

    return () => {
      cancelled = true;
    };
  }, [patientId]);

  return (
    <Card className="min-w-0">
      <CardContent className="space-y-1 p-3">
        <h3 className="px-1 pb-1 text-xs font-bold text-white">Clinical Context</h3>

        <ContextRow
          icon={ClipboardList}
          label="Prior Reports"
          value={patientId ? (priorReportCount === null ? 'Loading...' : priorReportsLabel(priorReportCount)) : 'None available'}
          action={patientId && priorReportCount ? { label: 'View all', onClick: onViewPriorReports } : undefined}
        />

        <ContextRow
          icon={FileClock}
          label="Relevant History"
          value={
            !patientId
              ? 'No relevant history'
              : medicalHistory === null
              ? 'Loading...'
              : medicalHistory.length > 0
              ? medicalHistory.join(', ')
              : 'No relevant history'
          }
          action={patientId ? { label: 'Add', href: `/patients?patientId=${patientId}` } : undefined}
        />

        <ContextRow
          icon={NotebookPen}
          label="Clinical Notes"
          value="No notes added"
          action={{ label: 'Add', onClick: onAddNote }}
        />
      </CardContent>
    </Card>
  );
}

function priorReportsLabel(count: number): string {
  if (count === 0) return 'None available';
  return `${count} report${count === 1 ? '' : 's'}`;
}

interface ContextRowAction {
  label: string;
  onClick?: () => void;
  href?: string;
}

function ContextRow({
  icon: Icon,
  label,
  value,
  action,
}: {
  icon: typeof ClipboardList;
  label: string;
  value: string;
  action?: ContextRowAction;
}) {
  return (
    <div className="flex items-start justify-between gap-3 rounded-lg px-1 py-2">
      <div className="flex min-w-0 items-start gap-2">
        <Icon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-500" />
        <div className="min-w-0">
          <p className="text-xs font-medium text-slate-200">{label}</p>
          <p className="mt-0.5 truncate text-[11px] text-slate-500">{value}</p>
        </div>
      </div>
      {action &&
        (action.href ? (
          <a href={action.href} className="shrink-0 text-[11px] font-semibold text-blue-400 hover:text-blue-300">
            {action.label}
          </a>
        ) : (
          <button
            type="button"
            onClick={action.onClick}
            className="shrink-0 text-[11px] font-semibold text-blue-400 hover:text-blue-300"
          >
            {action.label}
          </button>
        ))}
    </div>
  );
}
