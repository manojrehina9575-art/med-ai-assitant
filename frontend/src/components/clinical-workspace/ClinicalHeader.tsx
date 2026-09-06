import { AlertTriangle, CheckCircle2, ExternalLink, MoreVertical, User2 } from 'lucide-react';
import type { ClinicalReportStatus, ClinicalWorkspaceStudy, QaIssue } from '@/types/clinicalWorkspace';

const reportStatusMeta: Record<ClinicalReportStatus, { label: string; badge: string }> = {
  DRAFT: { label: 'Draft', badge: 'bg-blue-500/10 text-blue-300 border-blue-500/20' },
  REVIEW_REQUIRED: { label: 'Review Required', badge: 'bg-amber-500/10 text-amber-300 border-amber-500/25' },
  READY_TO_SIGN: { label: 'Ready To Sign', badge: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/25' },
  SIGNED: { label: 'Signed', badge: 'bg-slate-500/10 text-slate-300 border-slate-500/20' },
};

interface ClinicalHeaderProps {
  study: ClinicalWorkspaceStudy;
  reportStatus: ClinicalReportStatus;
  contextLabel?: string;
  visibleQaIssues: QaIssue[];
}

export function ClinicalHeader({ study, reportStatus, contextLabel = 'Demo case', visibleQaIssues }: ClinicalHeaderProps) {
  const status = reportStatusMeta[reportStatus];
  const hasHighSeverityIssues = visibleQaIssues.some((issue) => issue.severity === 'CRITICAL' || issue.severity === 'HIGH');
  const facts = [
    { label: 'Accession', value: study.accessionNumber },
    { label: 'Study Date', value: study.studyDate },
    { label: 'Modality', value: study.modality },
    { label: 'Study Description', value: study.studyType },
  ];

  return (
    <section
      className="rounded-xl border px-5 py-4"
      style={{ background: 'var(--surface, #111827)', borderColor: 'var(--clr-border, #1e2d45)' }}
    >
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div className="flex min-w-0 items-center gap-3">
          <div
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
            style={{ background: 'var(--surface-2, #1a2235)' }}
          >
            <User2 className="h-5 w-5 text-slate-400" />
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="truncate text-xl font-bold text-white">{study.patient.fullName}</h1>
              <span className="badge badge-slate text-[10px]">{contextLabel}</span>
            </div>
            <p className="mt-0.5 text-xs font-mono" style={{ color: 'var(--clr-text-3)' }}>
              MRN: {study.patient.medicalRecordNumber}
            </p>
          </div>
        </div>

        <div className="flex flex-wrap gap-x-8 gap-y-3 xl:min-w-0">
          {facts.map(({ label, value }) => (
            <div key={label} className="min-w-0">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{label}</p>
              <p className="mt-1 truncate text-sm font-semibold text-slate-100">{value}</p>
            </div>
          ))}
        </div>

        <div className="flex shrink-0 items-center gap-3">
          {reportStatus === 'REVIEW_REQUIRED' ? (
            <div className="flex items-center gap-2 rounded-lg border border-red-500/25 bg-red-950/25 px-3 py-2">
              <AlertTriangle className="h-4 w-4 shrink-0 text-red-300" />
              <div className="leading-tight">
                <p className="text-xs font-bold text-red-200">Review Required</p>
                <p className="text-[10px] text-red-300/80">
                  {hasHighSeverityIssues ? 'Potential QA issues detected' : 'Clinician review required'}
                </p>
              </div>
            </div>
          ) : (
            <div className="text-right">
              <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-slate-500">Status</p>
              <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-bold ${status.badge}`}>
                {reportStatus === 'SIGNED' && <CheckCircle2 className="h-3 w-3" />}
                {status.label}
              </span>
            </div>
          )}

          <button
            type="button"
            disabled
            title="No PACS integration is connected for this workspace yet."
            className="inline-flex h-9 items-center gap-1.5 rounded-lg border px-3 text-xs font-semibold text-slate-500 opacity-60"
            style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'rgba(255,255,255,0.03)' }}
          >
            Open in PACS
            <ExternalLink className="h-3.5 w-3.5" />
          </button>

          <button
            type="button"
            disabled
            title="No additional actions available yet."
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border text-slate-500 opacity-60"
            style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'rgba(255,255,255,0.03)' }}
          >
            <MoreVertical className="h-4 w-4" />
          </button>
        </div>
      </div>
    </section>
  );
}
