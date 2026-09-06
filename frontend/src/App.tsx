import { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { bootstrapSession } from '@/services/api';
import { AuthLayout } from '@/components/layout/AuthLayout';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { PatientsPage } from '@/pages/PatientsPage';
import { UploadPage } from '@/pages/UploadPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { AnalysisPage } from '@/pages/AnalysisPage';
import { BloodReportPage } from '@/pages/BloodReportPage';
import { KnowledgeBasePage } from '@/pages/KnowledgeBasePage';
import { ChatPage } from '@/pages/ChatPage';
import { WorkflowsPage } from '@/pages/WorkflowsPage';
import { WorklistPage } from '@/pages/WorklistPage';
import { CompliancePage } from '@/pages/CompliancePage';
import { FineTuningPage } from '@/pages/FineTuningPage';
import { ObservabilityPage } from '@/pages/ObservabilityPage';
import { ClinicalWorkspacePage } from '@/pages/ClinicalWorkspacePage';
import { AnatomyPage } from '@/pages/AnatomyPage';
import { QaAnalyticsPage } from '@/pages/QaAnalyticsPage';
import { IntegrationsPage } from '@/pages/IntegrationsPage';

export default function App() {
  // Nothing about the session survives a reload in memory — by design, so no token is ever written
  // to disk. This exchanges the httpOnly refresh cookie for a fresh access token on load, which is
  // what keeps the user signed in across reloads. Route guards wait on `isBootstrapped`.
  useEffect(() => {
    void bootstrapSession();
  }, []);

  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Route>

      <Route element={<DashboardLayout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/patients" element={<PatientsPage />} />
        <Route path="/worklist" element={<WorklistPage />} />
        <Route path="/clinical-workspace" element={<ClinicalWorkspacePage />} />
        <Route path="/clinical-workspace/:reviewId" element={<ClinicalWorkspacePage />} />
        <Route path="/qa-analytics" element={<QaAnalyticsPage />} />
        <Route path="/anatomy" element={<AnatomyPage />} />
        <Route path="/integrations" element={<IntegrationsPage />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/workflows" element={<WorkflowsPage />} />
        <Route path="/analysis" element={<AnalysisPage />} />
        <Route path="/blood-reports" element={<BloodReportPage />} />
        <Route path="/knowledge" element={<KnowledgeBasePage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/chat/:sessionId" element={<ChatPage />} />
        <Route path="/compliance" element={<CompliancePage />} />
        <Route path="/finetuning" element={<FineTuningPage />} />
        <Route path="/observability" element={<ObservabilityPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
