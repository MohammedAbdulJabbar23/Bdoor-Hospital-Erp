import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Bell, Search, LogOut, UserCircle2 } from 'lucide-react';
import { LangSwitcher } from '../ui/LangSwitcher';
import { Avatar } from '../ui/Avatar';
import { useAuthStore } from '../auth/authStore';
import { cn } from '../ui/cn';

export function Topbar() {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  const [profileOpen, setProfileOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);

  const profileRef = useRef<HTMLDivElement>(null);
  const notifRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
        setNotifOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const onLogout = () => {
    clear();
    navigate('/login', { replace: true });
  };

  const today = new Date().toLocaleDateString(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-4 border-b border-ink-200 bg-white/80 px-6 backdrop-blur">
      <div className="relative max-w-xl flex-1">
        <Search
          size={16}
          className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400"
        />
        <input
          type="search"
          placeholder={t('nav.search') ?? ''}
          className="h-10 w-full rounded-lg border border-ink-200 bg-ink-50 ps-9 pe-3 text-sm placeholder:text-ink-400 focus:border-brand-500 focus:bg-white"
        />
      </div>

      <div className="hidden text-end text-xs text-ink-500 sm:block">
        <div className="font-medium text-ink-700">{t('common.today')}</div>
        <div>{today}</div>
      </div>

      <LangSwitcher />

      <div ref={notifRef} className="relative">
        <button
          type="button"
          onClick={() => setNotifOpen((v) => !v)}
          className="relative flex h-10 w-10 items-center justify-center rounded-lg text-ink-600 hover:bg-ink-100"
          aria-label="notifications"
        >
          <Bell size={18} />
          <span className="absolute end-2.5 top-2 h-2 w-2 rounded-full bg-brand-600 ring-2 ring-white" />
        </button>
        {notifOpen && (
          <div className="absolute end-0 mt-2 w-80 rounded-xl border border-ink-200 bg-white p-3 shadow-elevated">
            <div className="mb-2 flex items-center justify-between px-1">
              <h4 className="text-sm font-semibold text-ink-900">{t('nav.notifications')}</h4>
            </div>
            <p className="px-1 py-3 text-sm text-ink-500">{t('nav.noNotifications')}</p>
          </div>
        )}
      </div>

      <div ref={profileRef} className="relative">
        <button
          type="button"
          onClick={() => setProfileOpen((v) => !v)}
          className={cn(
            'flex items-center gap-2 rounded-lg p-1 pe-2 text-start hover:bg-ink-100',
            profileOpen && 'bg-ink-100',
          )}
        >
          <Avatar name={user?.fullName ?? 'User'} size={32} />
          <div className="hidden text-xs leading-tight sm:block">
            <div className="max-w-[140px] truncate font-medium text-ink-900">
              {user?.fullName}
            </div>
            <div className="max-w-[140px] truncate text-ink-500">
              {user?.roles.slice(0, 2).join(' · ')}
            </div>
          </div>
        </button>
        {profileOpen && (
          <div className="absolute end-0 mt-2 w-64 rounded-xl border border-ink-200 bg-white p-2 shadow-elevated">
            <div className="border-b border-ink-100 px-3 py-3">
              <div className="text-sm font-semibold text-ink-900">{user?.fullName}</div>
              <div className="text-xs text-ink-500">@{user?.username}</div>
              <div className="mt-1 flex flex-wrap gap-1">
                {user?.roles.map((r) => (
                  <span
                    key={r}
                    className="rounded bg-ink-100 px-1.5 py-0.5 text-[10px] font-medium text-ink-700"
                  >
                    {r}
                  </span>
                ))}
              </div>
            </div>
            <button
              type="button"
              className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-ink-700 hover:bg-ink-50"
            >
              <UserCircle2 size={16} />
              {t('nav.profile')}
            </button>
            <button
              type="button"
              onClick={onLogout}
              className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-brand-700 hover:bg-brand-50"
            >
              <LogOut size={16} />
              {t('nav.logout')}
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
