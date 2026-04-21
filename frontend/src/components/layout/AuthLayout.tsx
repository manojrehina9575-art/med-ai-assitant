import { Outlet, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Stethoscope } from 'lucide-react';

export function AuthLayout() {
  const { isAuthenticated } = useAuthStore();

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="flex min-h-screen">
      <div className="hidden w-1/2 bg-primary lg:flex lg:flex-col lg:items-center lg:justify-center">
        <div className="text-center text-primary-foreground">
          <div className="mb-6 flex justify-center">
            <div className="flex h-20 w-20 items-center justify-center rounded-2xl bg-white/10 backdrop-blur">
              <Stethoscope className="h-12 w-12" />
            </div>
          </div>
          <h1 className="mb-3 text-4xl font-bold">Med AI Assistant</h1>
          <p className="max-w-md text-lg text-primary-foreground/80">
            AI-powered medical image and blood report analysis for hospitals.
            Faster diagnoses, better outcomes.
          </p>
          <div className="mt-8 flex justify-center gap-8 text-sm text-primary-foreground/60">
            <div>
              <p className="text-2xl font-bold text-primary-foreground">95%</p>
              <p>Accuracy</p>
            </div>
            <div>
              <p className="text-2xl font-bold text-primary-foreground">&lt;10s</p>
              <p>Analysis Time</p>
            </div>
            <div>
              <p className="text-2xl font-bold text-primary-foreground">24/7</p>
              <p>Availability</p>
            </div>
          </div>
        </div>
      </div>
      <div className="flex w-full items-center justify-center p-8 lg:w-1/2">
        <div className="w-full max-w-md">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
