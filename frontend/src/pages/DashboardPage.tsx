import { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { patientService } from '@/services/patientService';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Users, FileImage, Activity, Brain } from 'lucide-react';

export function DashboardPage() {
  const { fullName, tenantName, role } = useAuthStore();
  const [patientCount, setPatientCount] = useState(0);

  useEffect(() => {
    patientService.list(0, 1).then((res) => setPatientCount(res.totalElements)).catch(() => {});
  }, []);

  const stats = [
    { label: 'Total Patients', value: patientCount, icon: Users, color: 'text-blue-600 bg-blue-100' },
    { label: 'Files Uploaded', value: '-', icon: FileImage, color: 'text-green-600 bg-green-100' },
    { label: 'AI Analyses', value: 'Coming Soon', icon: Brain, color: 'text-purple-600 bg-purple-100' },
    { label: 'Active Sessions', value: '-', icon: Activity, color: 'text-orange-600 bg-orange-100' },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Welcome back, {fullName?.split(' ')[0]}</h1>
        <p className="mt-1 text-muted-foreground">
          {tenantName} &middot; {role?.replace('_', ' ')}
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <Card key={stat.label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {stat.label}
              </CardTitle>
              <div className={`rounded-lg p-2 ${stat.color}`}>
                <stat.icon className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{stat.value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3">
            <a
              href="/patients"
              className="flex items-center gap-3 rounded-lg border p-4 transition-colors hover:bg-accent"
            >
              <Users className="h-5 w-5 text-primary" />
              <div>
                <p className="font-medium">Manage Patients</p>
                <p className="text-sm text-muted-foreground">View, add, or edit patient records</p>
              </div>
            </a>
            <a
              href="/upload"
              className="flex items-center gap-3 rounded-lg border p-4 transition-colors hover:bg-accent"
            >
              <FileImage className="h-5 w-5 text-primary" />
              <div>
                <p className="font-medium">Upload Medical Files</p>
                <p className="text-sm text-muted-foreground">Upload X-rays, CTs, blood reports</p>
              </div>
            </a>
            <div className="flex items-center gap-3 rounded-lg border p-4 opacity-50">
              <Brain className="h-5 w-5 text-primary" />
              <div>
                <p className="font-medium">AI Analysis</p>
                <p className="text-sm text-muted-foreground">Coming in MVP 2</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Platform Status</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-sm">Backend API</span>
              <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-700">
                Online
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm">Database</span>
              <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-700">
                Connected
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm">AI Models (GPT-4o)</span>
              <span className="rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-700">
                MVP 2
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm">RAG Knowledge Base</span>
              <span className="rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-700">
                MVP 4
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm">Chat Memory</span>
              <span className="rounded-full bg-yellow-100 px-2.5 py-0.5 text-xs font-medium text-yellow-700">
                MVP 5
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
