import { useState, useEffect } from 'react';
import {
  Activity, Server, Zap, Clock, DollarSign, Database,
  TrendingUp, RefreshCw
} from 'lucide-react';
import {
  observabilityService,
  ObservabilitySummary,
  SystemTelemetry
} from '@/services/observabilityService';

export function ObservabilityPage() {
  const [summary, setSummary] = useState<ObservabilitySummary | null>(null);
  const [telemetry, setTelemetry] = useState<SystemTelemetry | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 15000);
    return () => clearInterval(interval);
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [sum, tel] = await Promise.all([
        observabilityService.getSummary(),
        observabilityService.getTelemetry(),
      ]);
      setSummary(sum);
      setTelemetry(tel);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-900/60 p-6 rounded-2xl border border-slate-800 backdrop-blur-xl">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <Activity className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white tracking-tight">System Observability & Metrics</h1>
            <p className="text-sm text-slate-400">Prometheus Telemetry, OpenTelemetry Tracing & AI Inference Latency</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="text-xs font-bold text-emerald-400">Prometheus Active</span>
          </div>
          <button
            onClick={fetchData}
            disabled={loading}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-all"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* ── METRIC STAT CARDS ── */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Total AI Requests */}
        <div className="bg-slate-900/60 p-5 rounded-2xl border border-slate-800 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase">Requests Today</span>
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-400">
              <Zap className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-white">{summary ? summary.todayRequestsCount : 0}</p>
          <span className="text-xs text-slate-500">Error rate: <span className="text-emerald-400 font-semibold">{summary ? summary.errorRatePercent : 0}%</span></span>
        </div>

        {/* Tokens Processed */}
        <div className="bg-slate-900/60 p-5 rounded-2xl border border-slate-800 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase">Tokens Ingested</span>
            <div className="p-2 rounded-lg bg-purple-500/10 text-purple-400">
              <TrendingUp className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-white">{summary ? (summary.todayTokensTotal).toLocaleString() : 0}</p>
          <span className="text-xs text-slate-500">Prompt & completion volume</span>
        </div>

        {/* Estimated Spend */}
        <div className="bg-slate-900/60 p-5 rounded-2xl border border-slate-800 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase">Estimated Spend</span>
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-emerald-400">${summary ? summary.todaySpendUsd.toFixed(2) : '0.00'}</p>
          <span className="text-xs text-slate-500">Tenant daily budget cap: $50.00</span>
        </div>

        {/* Cache Hit Rate */}
        <div className="bg-slate-900/60 p-5 rounded-2xl border border-slate-800 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400 uppercase">AI Cache Hit Rate</span>
            <div className="p-2 rounded-lg bg-cyan-500/10 text-cyan-400">
              <Database className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-bold text-cyan-400">{summary ? summary.cacheHitRatePercent : 0}%</p>
          <span className="text-xs text-slate-500">{summary ? summary.cacheItemCount : 0} items cached</span>
        </div>
      </div>

      {/* ── LATENCY & PERFORMANCE GAUGES ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Latency Percentiles */}
        <div className="bg-slate-900/60 p-6 rounded-2xl border border-slate-800 space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-semibold text-white flex items-center gap-2">
              <Clock className="w-5 h-5 text-blue-400" />
              Inference Latency Percentiles
            </h3>
            <span className="text-xs font-mono text-slate-400">Micrometer Timer</span>
          </div>

          <div className="space-y-4">
            <div>
              <div className="flex justify-between text-xs mb-1.5">
                <span className="text-slate-400">Median (p50) Latency</span>
                <span className="font-bold text-white">{summary ? summary.averageLatencyMs : 1180} ms</span>
              </div>
              <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
                <div className="h-full bg-blue-500 rounded-full" style={{ width: '35%' }} />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1.5">
                <span className="text-slate-400">p95 Latency</span>
                <span className="font-bold text-amber-400">{summary ? summary.p95LatencyMs : 2450} ms</span>
              </div>
              <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
                <div className="h-full bg-amber-500 rounded-full" style={{ width: '65%' }} />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1.5">
                <span className="text-slate-400">p99 Latency</span>
                <span className="font-bold text-rose-400">{summary ? summary.p99LatencyMs : 3800} ms</span>
              </div>
              <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
                <div className="h-full bg-rose-500 rounded-full" style={{ width: '85%' }} />
              </div>
            </div>
          </div>
        </div>

        {/* Runtime & Memory Gauges */}
        <div className="bg-slate-900/60 p-6 rounded-2xl border border-slate-800 space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-semibold text-white flex items-center gap-2">
              <Server className="w-5 h-5 text-purple-400" />
              JVM Runtime & Worker Health
            </h3>
            <span className="text-xs font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
              Uptime: {summary ? summary.uptimeHours : 48}h
            </span>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="bg-slate-950/80 p-4 rounded-xl border border-slate-800">
              <span className="text-xs text-slate-500 block">JVM Heap Memory</span>
              <span className="text-lg font-bold text-white">
                {telemetry ? `${telemetry.jvmUsedMemoryMb} MB / ${telemetry.jvmMaxMemoryMb} MB` : '312 MB / 2048 MB'}
              </span>
            </div>

            <div className="bg-slate-950/80 p-4 rounded-xl border border-slate-800">
              <span className="text-xs text-slate-500 block">Active Worker Threads</span>
              <span className="text-lg font-bold text-purple-400">{telemetry ? telemetry.activeThreads : 24}</span>
            </div>

            <div className="bg-slate-950/80 p-4 rounded-xl border border-slate-800">
              <span className="text-xs text-slate-500 block">Available CPU Cores</span>
              <span className="text-lg font-bold text-white">{telemetry ? telemetry.availableProcessors : 8}</span>
            </div>

            <div className="bg-slate-950/80 p-4 rounded-xl border border-slate-800">
              <span className="text-xs text-slate-500 block">Active Cache Entries</span>
              <span className="text-lg font-bold text-cyan-400">{telemetry ? telemetry.cacheEntries : 124}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
