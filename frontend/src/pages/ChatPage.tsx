import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  chatService,
  type ChatSession,
  type ChatMessage,
} from '@/services/chatService';
import { patientService } from '@/services/patientService';
import type { Patient } from '@/types';
import { Button } from '@/components/ui/Button';
import {
  MessageSquare,
  Plus,
  Search,
  Send,
  Loader2,
  Trash2,
  Download,
  Copy,
  Check,
  Bot,
  User,
  AlertTriangle,
  BookOpen,
  Sparkles,
  ChevronDown,
  Info,
  ShieldAlert,
  Users,
} from 'lucide-react';
import { cn } from '@/utils/cn';

const SUGGESTED_PROMPTS = [
  {
    title: 'Diagnostic Synthesis',
    prompt: 'Summarize the patient’s recent diagnostic imaging and blood lab findings. Highlight any critical anomalies.',
    badge: 'Clinical Synthesis',
  },
  {
    title: 'Allergy & Medication Check',
    prompt: 'Cross-reference the patient’s documented allergies and medical history against standard first-line therapies.',
    badge: 'Safety Sentinel',
  },
  {
    title: 'CAP Protocol Lookup',
    prompt: 'What is our hospital protocol for Community-Acquired Pneumonia (CAP) admission and empiric antibiotic therapy?',
    badge: 'RAG Protocol',
  },
  {
    title: 'Follow-up Care Plan',
    prompt: 'Generate an evidence-based clinical follow-up plan and recommended monitoring parameters for this patient.',
    badge: 'Care Plan',
  },
];

export function ChatPage() {
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSession, setCurrentSession] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [includeRag, setIncludeRag] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [expandedCitationIndex, setExpandedCitationIndex] = useState<string | null>(null);

  // New consultation modal state
  const [showNewModal, setShowNewModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [selectedPatientId, setSelectedPatientId] = useState<string>('');
  const [patients, setPatients] = useState<Patient[]>([]);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Load patients for dropdown
  const loadPatients = useCallback(async () => {
    try {
      const res = await patientService.list(0, 100);
      setPatients(res.content);
    } catch {
      // silent
    }
  }, []);

  // Load chat sessions
  const loadSessions = useCallback(async () => {
    setLoadingSessions(true);
    try {
      const res = await chatService.listSessions(undefined, 0, 50);
      setSessions(res.content);
      if (res.content.length > 0 && !currentSession) {
        selectSession(res.content[0].id);
      }
    } catch {
      // silent
    } finally {
      setLoadingSessions(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
    loadPatients();
  }, [loadSessions, loadPatients]);

  const selectSession = async (sessionId: string) => {
    setLoadingMessages(true);
    try {
      const session = await chatService.getSession(sessionId);
      setCurrentSession(session);
      setMessages(session.messages || []);
    } catch {
      // silent
    } finally {
      setLoadingMessages(false);
    }
  };

  const handleCreateSession = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const session = await chatService.createSession({
        patientId: selectedPatientId || undefined,
        title: newTitle.trim() || undefined,
      });
      setShowNewModal(false);
      setNewTitle('');
      setSelectedPatientId('');
      await loadSessions();
      await selectSession(session.id);
    } catch {
      // error handled by api interceptor
    }
  };

  const handleDeleteSession = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this consultation session?')) return;
    try {
      await chatService.deleteSession(sessionId);
      if (currentSession?.id === sessionId) {
        setCurrentSession(null);
        setMessages([]);
      }
      loadSessions();
    } catch {
      // error
    }
  };

  const handleSendMessage = async (customContent?: string) => {
    const textToSend = customContent || inputMessage;
    if (!textToSend.trim() || sending) return;

    // Auto-create session if none active
    let activeSessionId = currentSession?.id;
    if (!activeSessionId) {
      try {
        const created = await chatService.createSession({
          patientId: selectedPatientId || undefined,
          title: textToSend.slice(0, 40) + '...',
        });
        activeSessionId = created.id;
        setCurrentSession(created);
        loadSessions();
      } catch {
        return;
      }
    }

    // Optimistic user message
    const tempUserMsg: ChatMessage = {
      id: 'temp-' + Date.now(),
      sessionId: activeSessionId,
      role: 'USER',
      content: textToSend,
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, tempUserMsg]);
    setInputMessage('');
    setSending(true);

    try {
      const assistantMsg = await chatService.sendMessage(activeSessionId, textToSend, includeRag);
      setMessages((prev) => [...prev.filter((m) => m.id !== tempUserMsg.id), tempUserMsg, assistantMsg]);
      // refresh session info
      loadSessions();
    } catch {
      // fallback
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  };

  const handleExportTranscript = async () => {
    if (!currentSession) return;
    try {
      const transcript = await chatService.exportTranscript(currentSession.id);
      const blob = new Blob([transcript.formattedMarkdown], { type: 'text/markdown;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `consultation-${currentSession.title.replace(/\s+/g, '-').toLowerCase()}-${Date.now()}.md`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      // silent
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const filteredSessions = sessions.filter((s) => {
    const q = searchQuery.toLowerCase();
    return s.title.toLowerCase().includes(q) || (s.patientName && s.patientName.toLowerCase().includes(q));
  });

  return (
    <div className="flex h-[calc(100vh-4rem)] overflow-hidden bg-slate-950 text-slate-100">
      {/* LEFT SIDEBAR: Sessions */}
      <div className="w-80 flex flex-col border-r border-slate-800/80 bg-slate-900/40 backdrop-blur-md">
        {/* Header & New Chat Button */}
        <div className="p-4 border-b border-slate-800/80 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="p-1.5 rounded-lg bg-blue-500/10 text-blue-400 border border-blue-500/20">
                <MessageSquare className="h-4 w-4" />
              </div>
              <h2 className="font-semibold text-sm text-slate-200">Clinical Consultations</h2>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowNewModal(true)}
              className="border-blue-500/30 bg-blue-500/10 text-blue-400 hover:bg-blue-600 hover:text-white"
            >
              <Plus className="h-3.5 w-3.5 mr-1" />
              New
            </Button>
          </div>

          {/* Search */}
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-500" />
            <input
              type="text"
              placeholder="Search conversations..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 text-xs bg-slate-950 border border-slate-800 rounded-lg text-slate-200 placeholder-slate-500 focus:outline-none focus:border-blue-500/50"
            />
          </div>
        </div>

        {/* Sessions List */}
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {loadingSessions ? (
            <div className="flex items-center justify-center p-8 text-slate-500">
              <Loader2 className="h-5 w-5 animate-spin mr-2" />
              <span className="text-xs">Loading sessions...</span>
            </div>
          ) : filteredSessions.length === 0 ? (
            <div className="text-center p-6 text-xs text-slate-500 space-y-2">
              <p>No consultations found</p>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowNewModal(true)}
                className="text-blue-400 text-xs hover:bg-slate-800"
              >
                Start a new consultation
              </Button>
            </div>
          ) : (
            filteredSessions.map((s) => {
              const isActive = currentSession?.id === s.id;
              return (
                <div
                  key={s.id}
                  onClick={() => selectSession(s.id)}
                  className={cn(
                    'group flex items-start justify-between p-2.5 rounded-lg cursor-pointer transition-all border',
                    isActive
                      ? 'bg-blue-600/10 border-blue-500/30 text-white'
                      : 'border-transparent text-slate-400 hover:bg-slate-800/60 hover:text-slate-200'
                  )}
                >
                  <div className="min-w-0 flex-1 pr-2">
                    <p className={cn('text-xs font-medium truncate', isActive ? 'text-blue-300' : 'text-slate-300')}>
                      {s.title}
                    </p>
                    <div className="flex items-center gap-2 mt-1 text-[10px] text-slate-500">
                      {s.patientName && (
                        <span className="inline-flex items-center text-blue-400 bg-blue-500/10 px-1 rounded border border-blue-500/20">
                          {s.patientName}
                        </span>
                      )}
                      <span>{new Date(s.updatedAt).toLocaleDateString()}</span>
                      {s.messageCount !== undefined && s.messageCount > 0 && (
                        <span>• {s.messageCount} msgs</span>
                      )}
                    </div>
                  </div>
                  <button
                    onClick={(e) => handleDeleteSession(s.id, e)}
                    className="opacity-0 group-hover:opacity-100 p-1 text-slate-500 hover:text-red-400 hover:bg-red-500/10 rounded transition-all"
                    title="Delete consultation"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              );
            })
          )}
        </div>

        {/* Safety & Protocol Footer */}
        <div className="p-3 border-t border-slate-800/80 bg-slate-950/40 text-[11px] text-slate-500 flex items-center justify-between">
          <span className="flex items-center gap-1.5 text-emerald-400">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
            Active Memory Guard
          </span>
          <span className="text-slate-400 font-mono text-[10px]">RLS ENFORCED</span>
        </div>
      </div>

      {/* RIGHT MAIN CHAT PANE */}
      <div className="flex-1 flex flex-col bg-slate-950">
        {/* Header HUD */}
        <div className="h-14 border-b border-slate-800/80 px-6 flex items-center justify-between bg-slate-900/30 backdrop-blur-md">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <Bot className="h-5 w-5 text-blue-400" />
              <h1 className="font-semibold text-sm text-slate-100">
                {currentSession ? currentSession.title : 'New Clinical Consultation'}
              </h1>
            </div>

            {currentSession?.patientName && (
              <div className="flex items-center gap-1.5 bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded-full text-xs text-blue-300">
                <Users className="h-3 w-3" />
                <span>Patient: {currentSession.patientName}</span>
                {currentSession.patientMrn && (
                  <span className="text-slate-400 font-mono text-[10px]">({currentSession.patientMrn})</span>
                )}
              </div>
            )}
          </div>

          {/* Actions */}
          <div className="flex items-center gap-3">
            {/* RAG Toggle */}
            <button
              onClick={() => setIncludeRag(!includeRag)}
              className={cn(
                'flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border transition-all',
                includeRag
                  ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                  : 'bg-slate-800 text-slate-400 border-slate-700 hover:text-slate-200'
              )}
              title="Toggle Grounding in Hospital Protocols & Knowledge Base"
            >
              <BookOpen className="h-3.5 w-3.5" />
              <span>Hospital Protocols: {includeRag ? 'ON' : 'OFF'}</span>
            </button>

            {/* Export Transcript */}
            {currentSession && messages.length > 0 && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleExportTranscript}
                className="border-slate-800 bg-slate-900 text-slate-300 hover:bg-slate-800 hover:text-white text-xs"
              >
                <Download className="h-3.5 w-3.5 mr-1" />
                Export
              </Button>
            )}
          </div>
        </div>

        {/* Messages Stream */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loadingMessages ? (
            <div className="flex h-full items-center justify-center text-slate-500">
              <Loader2 className="h-6 w-6 animate-spin mr-2" />
              <span className="text-sm">Loading consultation history...</span>
            </div>
          ) : messages.length === 0 ? (
            /* Empty state with suggested clinical prompts */
            <div className="max-w-2xl mx-auto mt-8 space-y-6">
              <div className="text-center space-y-2">
                <div className="inline-flex p-3 rounded-2xl bg-gradient-to-br from-blue-600/20 to-indigo-600/20 border border-blue-500/30 text-blue-400 mb-2">
                  <Sparkles className="h-8 w-8" />
                </div>
                <h3 className="text-lg font-bold text-slate-100">Med-AI Clinical Assistant</h3>
                <p className="text-xs text-slate-400 max-w-md mx-auto">
                  Multi-turn conversational AI with patient context memory, diagnostic imaging synthesis, and verified hospital protocol grounding.
                </p>
              </div>

              {/* Suggestions grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {SUGGESTED_PROMPTS.map((item, idx) => (
                  <div
                    key={idx}
                    onClick={() => handleSendMessage(item.prompt)}
                    className="group p-3.5 rounded-xl border border-slate-800 bg-slate-900/40 hover:bg-slate-900 hover:border-blue-500/40 cursor-pointer transition-all space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-slate-200 group-hover:text-blue-300 transition-colors">
                        {item.title}
                      </span>
                      <span className="text-[10px] bg-blue-500/10 text-blue-400 border border-blue-500/20 px-1.5 py-0.5 rounded">
                        {item.badge}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 leading-relaxed">{item.prompt}</p>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            messages.map((msg, index) => {
              const isUser = msg.role === 'USER';
              const hasRedFlag = msg.safetyFlags?.includes('RED_FLAG_EMERGENCY');
              const hasAllergyFlag = msg.safetyFlags?.includes('ALLERGY_CONFLICT_DETECTED');

              return (
                <div
                  key={msg.id || index}
                  className={cn(
                    'flex gap-4 max-w-4xl mx-auto',
                    isUser ? 'justify-end' : 'justify-start'
                  )}
                >
                  {/* Assistant Avatar */}
                  {!isUser && (
                    <div className="h-8 w-8 rounded-lg bg-blue-600/20 border border-blue-500/30 flex items-center justify-center shrink-0 text-blue-400 mt-1">
                      <Bot className="h-4 w-4" />
                    </div>
                  )}

                  {/* Message Bubble */}
                  <div
                    className={cn(
                      'rounded-2xl p-4 space-y-3 max-w-[85%] border transition-all text-sm leading-relaxed',
                      isUser
                        ? 'bg-blue-600 text-white border-blue-500 rounded-tr-sm'
                        : 'bg-slate-900/70 border-slate-800/80 text-slate-200 rounded-tl-sm shadow-xl'
                    )}
                  >
                    {/* Safety Alert Banners */}
                    {hasRedFlag && (
                      <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-300 text-xs flex items-start gap-2">
                        <ShieldAlert className="h-4 w-4 shrink-0 text-red-400 mt-0.5" />
                        <div>
                          <p className="font-semibold">🚨 Acute Red-Flag Emergency Detected</p>
                          <p className="text-[11px] text-red-300/80">Immediate bedside evaluation and resuscitation protocol advised.</p>
                        </div>
                      </div>
                    )}

                    {hasAllergyFlag && (
                      <div className="p-2.5 bg-amber-500/10 border border-amber-500/30 rounded-lg text-amber-300 text-xs flex items-center gap-2">
                        <AlertTriangle className="h-4 w-4 shrink-0 text-amber-400" />
                        <span>Allergy Sentinel: Patient has documented sensitivities to discussed compounds.</span>
                      </div>
                    )}

                    {/* Content */}
                    <div className="whitespace-pre-wrap select-text">{msg.content}</div>

                    {/* Citations Box for Assistant */}
                    {!isUser && msg.citations && msg.citations.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-slate-800 space-y-2">
                        <p className="text-[11px] font-semibold text-slate-400 flex items-center gap-1.5">
                          <BookOpen className="h-3.5 w-3.5 text-blue-400" />
                          Grounded Hospital Protocols & Citations ({msg.citations.length})
                        </p>
                        <div className="grid grid-cols-1 gap-1.5">
                          {msg.citations.map((c, cIdx) => {
                            const isExpanded = expandedCitationIndex === `${msg.id}-${cIdx}`;
                            return (
                              <div
                                key={cIdx}
                                className="border border-slate-800 bg-slate-950/60 rounded-lg p-2 text-xs space-y-1"
                              >
                                <div
                                  onClick={() =>
                                    setExpandedCitationIndex(isExpanded ? null : `${msg.id}-${cIdx}`)
                                  }
                                  className="flex items-center justify-between cursor-pointer text-blue-300 hover:text-blue-200"
                                >
                                  <span className="font-medium truncate">
                                    [Citation {cIdx + 1}] {c.title}
                                  </span>
                                  <span className="text-[10px] text-slate-500 flex items-center gap-1">
                                    {c.documentType}
                                    <ChevronDown
                                      className={cn(
                                        'h-3 w-3 transition-transform',
                                        isExpanded ? 'rotate-180' : ''
                                      )}
                                    />
                                  </span>
                                </div>
                                {isExpanded && (
                                  <p className="text-[11px] text-slate-400 italic pt-1 border-t border-slate-800/60">
                                    {c.excerpt}
                                  </p>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}

                    {/* Message Metadata & Copy Button */}
                    <div className="flex items-center justify-between pt-1 text-[10px] text-slate-500">
                      <div className="flex items-center gap-2">
                        <span>{new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                        {!isUser && msg.modelUsed && (
                          <span className="font-mono bg-slate-800/60 px-1 rounded text-slate-400">
                            {msg.modelUsed}
                          </span>
                        )}
                        {!isUser && msg.totalTokens && (
                          <span>• {msg.totalTokens} tokens</span>
                        )}
                      </div>

                      <button
                        onClick={() => copyToClipboard(msg.content, msg.id)}
                        className="hover:text-slate-300 p-0.5 rounded transition-colors"
                        title="Copy message"
                      >
                        {copiedId === msg.id ? (
                          <Check className="h-3.5 w-3.5 text-emerald-400" />
                        ) : (
                          <Copy className="h-3.5 w-3.5" />
                        )}
                      </button>
                    </div>
                  </div>

                  {/* User Avatar */}
                  {isUser && (
                    <div className="h-8 w-8 rounded-lg bg-blue-600 flex items-center justify-center shrink-0 text-white mt-1">
                      <User className="h-4 w-4" />
                    </div>
                  )}
                </div>
              );
            })
          )}

          {/* Sending Indicator */}
          {sending && (
            <div className="flex gap-4 max-w-4xl mx-auto items-center text-slate-400 text-xs">
              <div className="h-8 w-8 rounded-lg bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400">
                <Bot className="h-4 w-4" />
              </div>
              <div className="flex items-center gap-2 bg-slate-900/60 border border-slate-800 px-4 py-2.5 rounded-2xl">
                <Loader2 className="h-4 w-4 animate-spin text-blue-400" />
                <span>Synthesizing patient records, imaging context, and clinical guidelines...</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="p-4 border-t border-slate-800/80 bg-slate-900/40 backdrop-blur-md">
          <div className="max-w-4xl mx-auto space-y-2">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleSendMessage();
              }}
              className="flex items-end gap-2 bg-slate-950 border border-slate-800 rounded-2xl p-2 focus-within:border-blue-500/50 transition-colors shadow-lg"
            >
              <textarea
                ref={inputRef}
                rows={1}
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSendMessage();
                  }
                }}
                placeholder="Ask about patient diagnosis, lab findings, or hospital protocols (Enter to send, Shift+Enter for newline)..."
                className="flex-1 max-h-32 min-h-[38px] bg-transparent border-0 resize-none px-2 py-1.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none"
              />

              <Button
                type="submit"
                disabled={!inputMessage.trim() || sending}
                size="sm"
                className="h-9 w-9 p-0 rounded-xl bg-blue-600 hover:bg-blue-500 text-white shrink-0"
              >
                {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
              </Button>
            </form>

            <div className="flex items-center justify-between text-[11px] text-slate-500 px-1">
              <span className="flex items-center gap-1">
                <Info className="h-3 w-3 text-blue-400" />
                AI decision-support only. Always verify critical recommendations clinically.
              </span>
              <span>Med-AI Assistant v1.0</span>
            </div>
          </div>
        </div>
      </div>

      {/* NEW CONSULTATION MODAL */}
      {showNewModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <MessageSquare className="h-5 w-5 text-blue-400" />
                <h3 className="font-semibold text-base text-slate-100">New Clinical Consultation</h3>
              </div>
              <button
                onClick={() => setShowNewModal(false)}
                className="text-slate-500 hover:text-slate-300 text-sm"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateSession} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Consultation Title (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. Inpatient Sepsis Workup"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Link Patient Record (Optional)
                </label>
                <select
                  value={selectedPatientId}
                  onChange={(e) => setSelectedPatientId(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                >
                  <option value="">-- General Protocol Discussion (No Patient) --</option>
                  {patients.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.firstName} {p.lastName} (MRN: {p.medicalRecordNumber})
                    </option>
                  ))}
                </select>
                <p className="text-[11px] text-slate-400 mt-1">
                  Linking a patient automatically loads their medical history, allergies, and recent imaging/lab findings into the AI context.
                </p>
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setShowNewModal(false)}
                  className="border-slate-800 text-slate-300 hover:bg-slate-800"
                >
                  Cancel
                </Button>
                <Button type="submit" size="sm" className="bg-blue-600 hover:bg-blue-500 text-white">
                  Start Consultation
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
