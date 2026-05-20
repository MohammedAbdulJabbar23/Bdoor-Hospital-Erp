import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronLeft } from 'lucide-react';
import { useMemo } from 'react';
import { visibleNav } from '../nav/routes';
import { useAuthStore } from '../auth/authStore';
import { cn } from '../ui/cn';
import { Logo } from '../ui/Logo';

type Props = {
  collapsed: boolean;
  onToggle: () => void;
};

export function Sidebar({ collapsed, onToggle }: Props) {
  const { t } = useTranslation();
  const roles = useAuthStore((s) => s.user?.roles ?? []);
  const navGroups = useMemo(() => visibleNav(roles), [roles]);

  return (
    <aside
      className={cn(
        'group/sidebar fixed inset-y-0 start-0 z-30 flex flex-col border-e border-ink-200 bg-white transition-[width] duration-200',
        collapsed ? 'w-[68px]' : 'w-[252px]',
      )}
    >
      <div
        className={cn(
          'flex h-16 items-center border-b border-ink-100 px-4',
          collapsed ? 'justify-center' : 'justify-between',
        )}
      >
        {collapsed ? (
          <CollapsedLogo />
        ) : (
          <>
            <Logo />
            <button
              type="button"
              onClick={onToggle}
              aria-label="collapse sidebar"
              className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100 hover:text-ink-900"
            >
              <ChevronLeft size={16} className="rtl:rotate-180" />
            </button>
          </>
        )}
      </div>
      {collapsed && (
        <button
          type="button"
          onClick={onToggle}
          aria-label="expand sidebar"
          className="mx-3 mt-2 flex h-8 items-center justify-center rounded-md text-ink-500 hover:bg-ink-100 hover:text-ink-900"
        >
          <ChevronLeft size={16} className="rotate-180 rtl:rotate-0" />
        </button>
      )}

      <nav className="flex-1 overflow-y-auto px-3 py-4">
        {navGroups.map((group) => (
          <div key={group.i18nKey} className="mb-5 last:mb-0">
            {!collapsed && (
              <div className="mb-1.5 px-2 text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                {t(group.i18nKey)}
              </div>
            )}
            <ul className="space-y-0.5">
              {group.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.to === '/'}
                    className={({ isActive }) =>
                      cn(
                        'group flex items-center gap-3 rounded-lg px-2 py-2 text-sm font-medium transition-colors',
                        isActive
                          ? 'bg-brand-50 text-brand-700'
                          : 'text-ink-600 hover:bg-ink-50 hover:text-ink-900',
                        collapsed && 'justify-center',
                      )
                    }
                    title={collapsed ? t(item.i18nKey) : undefined}
                  >
                    <item.icon
                      size={18}
                      className="shrink-0 group-aria-[current=page]:text-brand-700"
                    />
                    {!collapsed && (
                      <>
                        <span className="flex-1 truncate">{t(item.i18nKey)}</span>
                        {item.comingSoon && (
                          <span className="rounded-md bg-ink-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-500">
                            {t('nav.comingSoon')}
                          </span>
                        )}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-ink-100 px-3 py-3">
        {!collapsed ? (
          <p className="px-1 text-[11px] leading-tight text-ink-400">
            v0.1.0 · Albudoor HMS
          </p>
        ) : (
          <p className="text-center text-[10px] text-ink-400">v0.1</p>
        )}
      </div>
    </aside>
  );
}

function CollapsedLogo() {
  return (
    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-white">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden>
        <path d="M10 3h4a1 1 0 0 1 1 1v5h5a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-5v5a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-5H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1h5V4a1 1 0 0 1 1-1Z" />
      </svg>
    </div>
  );
}
