import { Activity, ArrowRight, Crosshair, FileText, Scan } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';

const anatomyRegions = [
  'Head and Neck',
  'Chest',
  'Abdomen',
  'Pelvis',
  'Spine',
  'Extremities',
];

const mappingRows = [
  { finding: 'Pulmonary opacity', region: 'Chest', status: 'Awaiting source finding' },
  { finding: 'Vertebral compression', region: 'Spine', status: 'Awaiting source finding' },
  { finding: 'Renal lesion', region: 'Abdomen', status: 'Awaiting source finding' },
];

export function AnatomyPage() {
  return (
    <div className="space-y-6 max-w-[1400px]">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-3">
          <div
            className="flex h-10 w-10 items-center justify-center rounded-xl"
            style={{
              background: 'linear-gradient(135deg, #8b5cf6, #3b82f6)',
              boxShadow: '0 0 20px rgba(139,92,246,0.3)',
            }}
          >
            <Scan className="h-5 w-5 text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white">Anatomy</h1>
            <p className="text-sm mt-0.5" style={{ color: 'var(--clr-text-3)' }}>
              Finding-to-anatomy mapping workspace for imaging QA review.
            </p>
          </div>
        </div>

        <Link
          to="/clinical-workspace"
          className="inline-flex h-9 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold text-white transition-all"
          style={{ background: 'linear-gradient(135deg, #3b82f6, #2563eb)' }}
        >
          <FileText className="h-4 w-4" />
          Clinical Workspace
          <ArrowRight className="h-4 w-4" />
        </Link>
      </div>

      <div className="grid gap-5 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Crosshair className="h-4 w-4 text-violet-400" />
              Anatomy Map
            </CardTitle>
            <CardDescription>Static V1 placeholder, no 3D runtime loaded</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {anatomyRegions.map((region) => (
                <div
                  key={region}
                  className="flex min-h-24 items-center justify-center rounded-lg border border-dashed text-sm font-semibold text-slate-300"
                  style={{ borderColor: 'var(--clr-border-2, #243250)', background: 'var(--surface-2, #1a2235)' }}
                >
                  {region}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-cyan-400" />
              Finding Links
            </CardTitle>
            <CardDescription>Issue-to-region alignment</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {mappingRows.map(({ finding, region, status }) => (
              <div
                key={finding}
                className="rounded-lg border px-3 py-3"
                style={{ borderColor: 'var(--clr-border, #1e2d45)', background: 'var(--surface-2, #1a2235)' }}
              >
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-medium text-white">{finding}</p>
                  <span className="badge badge-purple text-[10px]">{region}</span>
                </div>
                <p className="mt-1 text-[11px]" style={{ color: 'var(--clr-text-3)' }}>{status}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
