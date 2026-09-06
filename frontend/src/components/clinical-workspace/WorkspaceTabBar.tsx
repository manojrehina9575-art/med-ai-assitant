import { Activity, FileClock, History, LayoutDashboard } from 'lucide-react';
import { cn } from '@/utils/cn';
import type { ClinicalContextTab } from '@/types/clinicalWorkspace';

interface WorkspaceTabBarProps {
  activeTab: ClinicalContextTab;
  onTabChange: (tab: ClinicalContextTab) => void;
}

const tabs: { id: ClinicalContextTab; label: string; icon: typeof History }[] = [
  { id: 'clinical-workspace', label: 'Clinical Workspace', icon: LayoutDashboard },
  { id: 'prior-studies', label: 'Prior Studies', icon: History },
  { id: 'timeline', label: 'Timeline', icon: FileClock },
  { id: 'audit', label: 'Audit Trail', icon: Activity },
];

export function WorkspaceTabBar({ activeTab, onTabChange }: WorkspaceTabBarProps) {
  return (
    <div className="flex min-w-max items-center gap-1 rounded-xl border border-slate-800 bg-slate-950/60 p-1.5">
      {tabs.map(({ id, label, icon: Icon }) => (
        <button
          key={id}
          type="button"
          onClick={() => onTabChange(id)}
          className={cn(
            'flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium transition-all',
            activeTab === id ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/20' : 'text-slate-400 hover:text-white'
          )}
        >
          <Icon className="h-3.5 w-3.5" />
          {label}
        </button>
      ))}
    </div>
  );
}
