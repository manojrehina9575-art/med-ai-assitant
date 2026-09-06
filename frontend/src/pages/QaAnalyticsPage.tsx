import { BarChart2, CheckCircle2, ClipboardCheck, TrendingDown, XCircle } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';

const qaMetrics = [
  { label: 'Reports reviewed', value: '0', icon: ClipboardCheck, color: '#3b82f6' },
  { label: 'QA issues', value: '0', icon: BarChart2, color: '#f59e0b' },
  { label: 'Confirmed issues', value: '0', icon: CheckCircle2, color: '#10b981' },
  { label: 'Dismissed issues', value: '0', icon: XCircle, color: '#ef4444' },
  { label: 'False-positive rate', value: '0%', icon: TrendingDown, color: '#8b5cf6' },
];

const issueCategories = [
  'Missed critical finding',
  'Laterality mismatch',
  'Prior comparison gap',
  'Measurement inconsistency',
  'Recommendation mismatch',
];

export function QaAnalyticsPage() {
  return (
    <div className="space-y-6 max-w-[1400px]">
      <div className="flex items-center gap-3">
        <div
          className="flex h-10 w-10 items-center justify-center rounded-xl"
          style={{
            background: 'linear-gradient(135deg, #f59e0b, #3b82f6)',
            boxShadow: '0 0 20px rgba(245,158,11,0.25)',
          }}
        >
          <BarChart2 className="h-5 w-5 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-white">QA Analytics</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
            Operational quality metrics for diagnostic imaging report review.
          </p>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {qaMetrics.map(({ label, value, icon: Icon, color }) => (
          <Card key={label}>
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg" style={{ background: `${color}18` }}>
                  <Icon className="h-4 w-4" style={{ color }} />
                </div>
                <span className="badge badge-slate text-[10px]">Planned</span>
              </div>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-extrabold text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>{value}</p>
              <p className="text-xs mt-1" style={{ color: 'var(--clr-text-3)' }}>{label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>QA Trend</CardTitle>
            <CardDescription>Reports reviewed, confirmed issues, dismissed issues, and false positives</CardDescription>
          </CardHeader>
          <CardContent>
            <div
              className="flex h-72 items-center justify-center rounded-lg border border-dashed text-sm text-slate-500"
              style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
            >
              QA metric timeline
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Issue Categories</CardTitle>
            <CardDescription>Future clinical QA taxonomy</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            {issueCategories.map((category) => (
              <div
                key={category}
                className="flex items-center justify-between rounded-lg border px-3 py-2"
                style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface-2, #1a2235)' }}
              >
                <span className="text-xs text-slate-300">{category}</span>
                <span className="text-[11px] font-semibold" style={{ color: 'var(--clr-text-3)' }}>0</span>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
