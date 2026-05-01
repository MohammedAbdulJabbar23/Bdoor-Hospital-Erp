import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { Breadcrumbs } from './Breadcrumbs';
import { cn } from '../ui/cn';

export function AppShell() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="min-h-screen bg-ink-50">
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((v) => !v)} />
      <div
        className={cn(
          'flex min-h-screen flex-col transition-[padding] duration-200',
          collapsed ? 'ps-[68px]' : 'ps-[252px]',
        )}
      >
        <Topbar />
        <div className="px-6 pt-4">
          <Breadcrumbs />
        </div>
        <main className="flex-1 px-6 pb-10 pt-4">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
