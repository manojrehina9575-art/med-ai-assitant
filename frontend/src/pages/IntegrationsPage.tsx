import { Activity, Database, FileText, KeyRound, PlugZap } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';

const integrations = [
  {
    title: 'PACS',
    sub: 'Imaging archive and study retrieval',
    icon: Database,
    color: '#3b82f6',
  },
  {
    title: 'RIS',
    sub: 'Orders, accessioning, and scheduling context',
    icon: Activity,
    color: '#10b981',
  },
  {
    title: 'Reporting System',
    sub: 'Draft report intake and final sign-off handoff',
    icon: FileText,
    color: '#f59e0b',
  },
  {
    title: 'Identity & Access',
    sub: 'SSO, roles, and enterprise authentication',
    icon: KeyRound,
    color: '#8b5cf6',
  },
];

export function IntegrationsPage() {
  return (
    <div className="space-y-6 max-w-[1200px]">
      <div className="flex items-center gap-3">
        <div
          className="flex h-10 w-10 items-center justify-center rounded-xl"
          style={{
            background: 'linear-gradient(135deg, #06b6d4, #3b82f6)',
            boxShadow: '0 0 20px rgba(6,182,212,0.25)',
          }}
        >
          <PlugZap className="h-5 w-5 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-white">Integrations</h1>
          <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
            PACS, RIS, reporting, and identity connections for imaging operations.
          </p>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {integrations.map(({ title, sub, icon: Icon, color }) => (
          <Card key={title}>
            <CardHeader>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg" style={{ background: `${color}18` }}>
                    <Icon className="h-4 w-4" style={{ color }} />
                  </div>
                  <CardTitle>{title}</CardTitle>
                </div>
                <span className="badge badge-slate text-[10px]">Planned</span>
              </div>
              <CardDescription>{sub}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="rounded-lg border border-dashed border-slate-700 bg-slate-950/30 px-3 py-2 text-xs text-slate-500">
                Not connected
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
