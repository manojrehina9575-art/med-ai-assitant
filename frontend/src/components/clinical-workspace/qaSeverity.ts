import type { QaSeverity } from '@/types/clinicalWorkspace';

export const qaSeverityMeta: Record<QaSeverity, { label: string; badge: string; tone: string; border: string; accent: string }> = {
  CRITICAL: {
    label: 'Critical',
    badge: 'bg-red-500/20 text-red-300 border-red-500/30',
    tone: 'text-red-300',
    border: 'border-red-500/30',
    accent: '#ef4444',
  },
  HIGH: {
    label: 'High',
    badge: 'bg-orange-500/10 text-orange-300 border-orange-500/30',
    tone: 'text-orange-300',
    border: 'border-orange-500/30',
    accent: '#f97316',
  },
  MEDIUM: {
    label: 'Medium',
    badge: 'bg-amber-500/10 text-amber-300 border-amber-500/25',
    tone: 'text-amber-300',
    border: 'border-amber-500/25',
    accent: '#f59e0b',
  },
  LOW: {
    label: 'Low',
    badge: 'bg-blue-500/10 text-blue-300 border-blue-500/20',
    tone: 'text-blue-300',
    border: 'border-blue-500/20',
    accent: '#3b82f6',
  },
  INFO: {
    label: 'Info',
    badge: 'bg-slate-500/10 text-slate-300 border-slate-500/20',
    tone: 'text-slate-300',
    border: 'border-slate-500/20',
    accent: '#64748b',
  },
};
