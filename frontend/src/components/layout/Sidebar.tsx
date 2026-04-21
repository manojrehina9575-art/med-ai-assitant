import { NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import {
  LayoutDashboard,
  Users,
  Upload,
  Settings,
  LogOut,
  Stethoscope,
  Brain,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/utils/cn';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/patients', label: 'Patients', icon: Users },
  { to: '/upload', label: 'Upload Files', icon: Upload },
  { to: '/analysis', label: 'AI Analysis', icon: Brain },
  { to: '/settings', label: 'Settings', icon: Settings },
];

export function Sidebar() {
  const { fullName, role, tenantName, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <aside className="flex h-screen w-64 flex-col border-r bg-card">
      <div className="flex items-center gap-3 border-b p-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Stethoscope className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-lg font-bold">Med AI</h1>
          <p className="text-xs text-muted-foreground">{tenantName}</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1 p-3">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                false
              )
            }
          >
            <item.icon className="h-4 w-4" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t p-4">
        <div className="mb-3">
          <p className="text-sm font-medium">{fullName}</p>
          <p className="text-xs text-muted-foreground">{role?.replace('_', ' ')}</p>
        </div>
        <Button variant="outline" size="sm" className="w-full" onClick={handleLogout}>
          <LogOut className="mr-2 h-4 w-4" />
          Logout
        </Button>
      </div>
    </aside>
  );
}
