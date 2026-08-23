import { useState, useEffect, useRef } from 'react';
import { Bell, CheckCheck, X, AlertTriangle, AlertCircle, Info, ExternalLink } from 'lucide-react';
import { notificationService, type Notification } from '@/services/notificationService';

const severityConfig = {
  CRITICAL: { icon: AlertTriangle, color: '#ef4444', bg: 'rgba(239,68,68,0.1)', border: 'rgba(239,68,68,0.3)' },
  WARNING:  { icon: AlertCircle,  color: '#f59e0b', bg: 'rgba(245,158,11,0.1)', border: 'rgba(245,158,11,0.3)' },
  INFO:     { icon: Info,          color: '#3b82f6', bg: 'rgba(59,130,246,0.1)',  border: 'rgba(59,130,246,0.3)' },
};

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1)  return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

interface Props {
  onUnreadChange?: (count: number) => void;
}

export function NotificationCenter({ onUnreadChange }: Props) {
  const [open, setOpen]               = useState(false);
  const [notifications, setList]      = useState<Notification[]>([]);
  const [unread, setUnread]           = useState(0);
  const [loading, setLoading]         = useState(false);
  const [markingAll, setMarkingAll]   = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  // ── Load notifications ───────────────────────────────────
  const load = async () => {
    try {
      setLoading(true);
      const data = await notificationService.list(0, 30);
      setList(data.notifications);
      setUnread(data.unreadCount);
      onUnreadChange?.(data.unreadCount);
    } catch {
      // silently ignore — bell just won't show count
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // Poll every 30 s for new notifications
    const t = setInterval(load, 30_000);
    return () => clearInterval(t);
  }, []);

  // ── Close on outside click ───────────────────────────────
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  // ── Handlers ────────────────────────────────────────────
  const handleMarkRead = async (id: string) => {
    await notificationService.markRead(id);
    setList((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    setUnread((c) => Math.max(0, c - 1));
    onUnreadChange?.(Math.max(0, unread - 1));
  };

  const handleMarkAll = async () => {
    setMarkingAll(true);
    await notificationService.markAllRead();
    setList((prev) => prev.map((n) => ({ ...n, isRead: true })));
    setUnread(0);
    onUnreadChange?.(0);
    setMarkingAll(false);
  };

  const cfg = (sev: string) =>
    severityConfig[sev as keyof typeof severityConfig] ?? severityConfig.INFO;

  // ── Render ───────────────────────────────────────────────
  return (
    <div style={{ position: 'relative' }} ref={panelRef}>
      {/* Bell button */}
      <button
        id="notification-bell-btn"
        onClick={() => { setOpen((o) => !o); if (!open) load(); }}
        style={{
          position: 'relative',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          width: 32, height: 32, borderRadius: 8,
          background: open ? 'rgba(59,130,246,0.15)' : 'var(--surface-2, #1a2235)',
          border: `1px solid ${open ? 'rgba(59,130,246,0.4)' : 'var(--clr-border, #1e2d45)'}`,
          color: open ? '#60a5fa' : 'var(--clr-text-2, #94a3b8)',
          cursor: 'pointer', transition: 'all 0.15s',
        }}
      >
        <Bell size={14} />
        {unread > 0 && (
          <span style={{
            position: 'absolute', top: -4, right: -4,
            minWidth: 16, height: 16, borderRadius: 999,
            background: '#ef4444', color: '#fff',
            fontSize: 10, fontWeight: 700,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            padding: '0 3px', lineHeight: 1,
            border: '2px solid var(--bg, #0a0f1e)',
          }}>
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          id="notification-panel"
          style={{
            position: 'absolute', top: 'calc(100% + 8px)', right: 0, zIndex: 999,
            width: 360, maxHeight: 520,
            background: 'var(--surface-1, #111827)',
            border: '1px solid var(--clr-border, #1e2d45)',
            borderRadius: 12, overflow: 'hidden',
            boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
            display: 'flex', flexDirection: 'column',
          }}
        >
          {/* Header */}
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '12px 16px',
            borderBottom: '1px solid var(--clr-border, #1e2d45)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Bell size={14} style={{ color: '#3b82f6' }} />
              <span style={{ fontWeight: 600, fontSize: 14, color: '#f1f5f9' }}>Notifications</span>
              {unread > 0 && (
                <span style={{
                  fontSize: 11, fontWeight: 700, color: '#ef4444',
                  background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)',
                  borderRadius: 999, padding: '1px 6px',
                }}>{unread} new</span>
              )}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {unread > 0 && (
                <button
                  id="mark-all-read-btn"
                  onClick={handleMarkAll}
                  disabled={markingAll}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 4,
                    fontSize: 11, color: '#60a5fa', background: 'none',
                    border: 'none', cursor: 'pointer', padding: '2px 6px', borderRadius: 4,
                  }}
                >
                  <CheckCheck size={12} /> Mark all read
                </button>
              )}
              <button
                onClick={() => setOpen(false)}
                style={{
                  background: 'none', border: 'none', color: '#64748b',
                  cursor: 'pointer', display: 'flex', alignItems: 'center',
                }}
              >
                <X size={14} />
              </button>
            </div>
          </div>

          {/* List */}
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {loading && notifications.length === 0 && (
              <div style={{ padding: 32, textAlign: 'center', color: '#64748b', fontSize: 13 }}>
                Loading…
              </div>
            )}
            {!loading && notifications.length === 0 && (
              <div style={{ padding: 32, textAlign: 'center', color: '#64748b', fontSize: 13 }}>
                <Bell size={32} style={{ margin: '0 auto 8px', opacity: 0.3, display: 'block' }} />
                No notifications
              </div>
            )}
            {notifications.map((n) => {
              const { icon: Icon, color, bg, border } = cfg(n.severity);
              return (
                <div
                  key={n.id}
                  style={{
                    display: 'flex', gap: 10, padding: '12px 16px',
                    background: n.isRead ? 'transparent' : 'rgba(59,130,246,0.04)',
                    borderBottom: '1px solid var(--clr-border, #1e2d45)',
                    transition: 'background 0.15s', cursor: 'default',
                  }}
                >
                  {/* Severity icon */}
                  <div style={{
                    flexShrink: 0, width: 30, height: 30, borderRadius: 8,
                    background: bg, border: `1px solid ${border}`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    marginTop: 2,
                  }}>
                    <Icon size={14} style={{ color }} />
                  </div>

                  {/* Content */}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
                      gap: 4,
                    }}>
                      <span style={{
                        fontSize: 13, fontWeight: n.isRead ? 400 : 600,
                        color: n.isRead ? '#94a3b8' : '#f1f5f9',
                        lineHeight: 1.3,
                      }}>
                        {n.title}
                      </span>
                      {!n.isRead && (
                        <button
                          onClick={() => handleMarkRead(n.id)}
                          style={{
                            flexShrink: 0, background: 'none', border: 'none',
                            color: '#64748b', cursor: 'pointer', padding: 2, borderRadius: 4,
                            lineHeight: 0,
                          }}
                          title="Mark as read"
                        >
                          <X size={11} />
                        </button>
                      )}
                    </div>
                    <p style={{
                      fontSize: 12, color: '#64748b', margin: '3px 0 0',
                      lineHeight: 1.4, overflow: 'hidden',
                      display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
                    }}>
                      {n.message}
                    </p>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
                      <span style={{ fontSize: 11, color: '#475569' }}>{timeAgo(n.createdAt)}</span>
                      {n.relatedEntityType && n.relatedEntityId && (
                        <span style={{
                          fontSize: 10, fontWeight: 600, color: '#3b82f6',
                          background: 'rgba(59,130,246,0.1)', borderRadius: 4, padding: '1px 5px',
                          display: 'flex', alignItems: 'center', gap: 3,
                        }}>
                          <ExternalLink size={9} /> View {n.relatedEntityType.toLowerCase()}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Unread dot */}
                  {!n.isRead && (
                    <div style={{
                      flexShrink: 0, width: 6, height: 6, borderRadius: 999,
                      background: '#3b82f6', marginTop: 6, alignSelf: 'flex-start',
                    }} />
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
