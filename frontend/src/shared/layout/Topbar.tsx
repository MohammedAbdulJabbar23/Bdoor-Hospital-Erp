import { useState, useRef, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell, Search, LogOut, UserCircle2, FlaskConical, Scan, HeartPulse, type LucideIcon } from 'lucide-react';
import { LangSwitcher } from '../ui/LangSwitcher';
import { Avatar } from '../ui/Avatar';
import { useAuthStore } from '../auth/authStore';
import { cn } from '../ui/cn';
import { searchVisits, Visit } from '@/features/reception/visits/api';

export function Topbar() {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const navigate = useNavigate();

  const [profileOpen, setProfileOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [lastSeenAt, setLastSeenAt] = useState<number>(() => {
    const v = window.localStorage.getItem('hms.notif.lastSeenAt');
    return v ? Number(v) : 0;
  });

  // Surface forwarded results returning to the originating visit.
  // Polls every 20s; finds visits where `resultsSummary` was attached recently.
  const { data: visitsPage } = useQuery({
    queryKey: ['notif-visits'],
    queryFn: () => searchVisits(null, null, 0, 40),
    refetchInterval: 20_000,
    enabled: !!user,
  });

  // Surface the ORIGINATING visit that just received forwarded results — NOT the forwarded child
  // (whose /clinical/exam page is empty). The parent keeps `endedAt` null while it's still in
  // progress, so we key off `resultsLastUpdatedAt` (set whenever a child returns results) instead.
  const returnedNotifs = useMemo(() => {
    const all = visitsPage?.content ?? [];
    const cutoff = Date.now() - 24 * 60 * 60 * 1000; // last 24h
    return all
      .filter((v) =>
        v.resultsSummary
        && v.origin !== 'FORWARDED'
        && v.resultsLastUpdatedAt != null
        && Date.parse(v.resultsLastUpdatedAt) > cutoff)
      .sort((a, b) => Date.parse(b.resultsLastUpdatedAt ?? '0') - Date.parse(a.resultsLastUpdatedAt ?? '0'))
      .slice(0, 8);
  }, [visitsPage]);

  const unread = returnedNotifs.filter((v) => Date.parse(v.resultsLastUpdatedAt ?? '0') > lastSeenAt).length;

  const markSeen = () => {
    const now = Date.now();
    setLastSeenAt(now);
    window.localStorage.setItem('hms.notif.lastSeenAt', String(now));
  };

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
          onClick={() => { setNotifOpen((v) => !v); if (!notifOpen) markSeen(); }}
          className="relative flex h-10 w-10 items-center justify-center rounded-lg text-ink-600 hover:bg-ink-100"
          aria-label="notifications"
        >
          <Bell size={18} />
          {unread > 0 && (
            <span className="absolute end-2 top-1.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-brand-600 px-1 text-[10px] font-semibold text-white ring-2 ring-white">
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </button>
        {notifOpen && (
          <div className="absolute end-0 mt-2 w-96 rounded-xl border border-ink-200 bg-white p-3 shadow-elevated">
            <div className="mb-2 flex items-center justify-between px-1">
              <h4 className="text-sm font-semibold text-ink-900">{t('nav.notifications')}</h4>
              <span className="text-[11px] text-ink-500">{t('nav.last24h')}</span>
            </div>
            {returnedNotifs.length === 0 ? (
              <p className="px-1 py-3 text-sm text-ink-500">{t('nav.noNotifications')}</p>
            ) : (
              <ul className="max-h-[60vh] divide-y divide-ink-100 overflow-y-auto">
                {returnedNotifs.map((v) => (
                  <NotifItem
                    key={v.id}
                    visit={v}
                    onOpen={() => { setNotifOpen(false); navigate(`/clinical/exam/${v.id}`); }}
                  />
                ))}
              </ul>
            )}
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

const TYPE_ICON: Record<string, LucideIcon> = {
  LABORATORY: FlaskConical,
  RADIOLOGY: Scan,
  ECO: HeartPulse,
};

function NotifItem({ visit, onOpen }: { visit: Visit; onOpen: () => void }) {
  const { t } = useTranslation();
  // The notification is on the ORIGINATING visit (e.g. a doctor consult). Use its own type icon
  // when known, else a generic results icon. The label is dept-agnostic because the parent visit
  // doesn't carry which child returned the results.
  const Icon = TYPE_ICON[visit.visitType] ?? FlaskConical;
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className="flex w-full items-start gap-3 px-2 py-2.5 text-start hover:bg-ink-50/60"
      >
        <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-700">
          <Icon size={14} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium text-ink-900">
            {visit.patientName} <span className="text-ink-500">· {visit.visitDisplayId}</span>
          </div>
          <div className="text-xs text-ink-500">{t('nav.resultsReturned')}</div>
          {visit.resultsSummary && (
            <div className="mt-1 line-clamp-2 rounded bg-ink-50 px-2 py-1 text-[11px] text-ink-700">
              {visit.resultsSummary}
            </div>
          )}
        </div>
      </button>
    </li>
  );
}
