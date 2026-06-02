import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Users,
  Wallet,
  BedDouble,
  ListOrdered,
  ArrowUpRight,
  UserPlus,
  CalendarPlus,
  CreditCard,
  Activity,
  Clock,
  AlertTriangle,
  type LucideIcon,
} from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/shared/ui/Card';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Badge } from '@/shared/ui/Badge';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useAuthStore } from '@/shared/auth/authStore';
import { getDashboardSummary, type DashboardSummary } from './api';

type Kpi = {
  i18nKey: string;
  value: string;
  subtitle?: string;
  icon: LucideIcon;
  tone: 'brand' | 'success' | 'warning' | 'info';
};

function buildKpis(s: DashboardSummary): Kpi[] {
  const pct = s.bedsTotal > 0 ? Math.round((s.bedsOccupied / s.bedsTotal) * 100) : 0;
  return [
    { i18nKey: 'dashboard.kpi.patientsToday',   value: String(s.patientsToday),                icon: Users,       tone: 'brand'   },
    { i18nKey: 'dashboard.kpi.pendingPayments', value: String(s.pendingPayments),              icon: Wallet,      tone: 'warning' },
    { i18nKey: 'dashboard.kpi.bedsOccupancy',   value: `${s.bedsOccupied} / ${s.bedsTotal}`, subtitle: `${pct}%`, icon: BedDouble, tone: 'info' },
    { i18nKey: 'dashboard.kpi.activeQueues',    value: String(s.activeQueues),                 icon: ListOrdered, tone: 'success' },
  ];
}

const KPI_META: Pick<Kpi, 'i18nKey' | 'icon' | 'tone'>[] = [
  { i18nKey: 'dashboard.kpi.patientsToday',   icon: Users,       tone: 'brand'   },
  { i18nKey: 'dashboard.kpi.pendingPayments', icon: Wallet,      tone: 'warning' },
  { i18nKey: 'dashboard.kpi.bedsOccupancy',   icon: BedDouble,   tone: 'info'    },
  { i18nKey: 'dashboard.kpi.activeQueues',    icon: ListOrdered, tone: 'success' },
];

export function DashboardPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: getDashboardSummary,
    refetchInterval: 30000,
  });

  const kpis = data ? buildKpis(data) : null;

  const attention = [
    {
      key: 'pendingPayments',
      count: data?.pendingPaymentsCount ?? 0,
      tone: 'warning' as const,
      icon: Wallet,
      title: t('dashboard.attention.pendingPaymentsTitle', { count: data?.pendingPaymentsCount ?? 0 }),
      body: t('dashboard.attention.pendingPaymentsBody'),
      to: '/cashier',
    },
    {
      key: 'labResults',
      count: data?.labResultsAwaiting ?? 0,
      tone: 'info' as const,
      icon: Activity,
      title: t('dashboard.attention.labResultsTitle', { count: data?.labResultsAwaiting ?? 0 }),
      body: t('dashboard.attention.labResultsBody'),
      to: '/departments/laboratory',
    },
    {
      key: 'bedExpiry',
      count: data?.bedsExpiringSoon ?? 0,
      tone: 'danger' as const,
      icon: Clock,
      title: t('dashboard.attention.bedExpiryTitle', { count: data?.bedsExpiringSoon ?? 0 }),
      body: t('dashboard.attention.bedExpiryBody'),
      to: '/departments/emergency',
    },
  ].filter((a) => a.count > 0);

  return (
    <>
      <PageHeader
        title={t('dashboard.welcome', { name: user?.fullName?.split(' ')[1] ?? user?.fullName ?? '' })}
        description={t('dashboard.summary')}
      />

      {isError && (
        <p className="mb-4 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800 ring-1 ring-inset ring-amber-200">
          {t('common.loadError', 'Could not load live metrics right now.')}
        </p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {isLoading || !kpis
          ? KPI_META.map((m) => <KpiTileSkeleton key={m.i18nKey} kpi={m} />)
          : kpis.map((k) => <KpiTile key={k.i18nKey} kpi={k} />)}
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle size={16} className="text-amber-500" />
              {t('dashboard.sections.attention')}
            </CardTitle>
            {!isLoading && attention.length > 0 && (
              <Badge tone="warning" dot>
                {attention.length}
              </Badge>
            )}
          </CardHeader>
          <CardBody className="space-y-3">
            {isLoading ? (
              <>
                <Skeleton className="h-14 w-full" />
                <Skeleton className="h-14 w-full" />
              </>
            ) : attention.length === 0 ? (
              <p className="px-1 py-4 text-sm text-ink-500">{t('dashboard.attention.empty')}</p>
            ) : (
              attention.map((a) => (
                <AttentionRow
                  key={a.key}
                  tone={a.tone}
                  icon={a.icon}
                  title={a.title}
                  body={a.body}
                  to={a.to}
                />
              ))
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.sections.shortcuts')}</CardTitle>
          </CardHeader>
          <CardBody className="grid grid-cols-2 gap-3 p-4">
            <Shortcut to="/reception/patients/new" icon={UserPlus} label={t('dashboard.shortcuts.registerPatient')} />
            <Shortcut to="/reception/appointments" icon={CalendarPlus} label={t('dashboard.shortcuts.newAppointment')} />
            <Shortcut to="/cashier" icon={CreditCard} label={t('dashboard.shortcuts.collectPayment')} />
            <Shortcut to="/departments/emergency" icon={BedDouble} label={t('dashboard.shortcuts.viewBeds')} />
          </CardBody>
        </Card>
      </div>

      <div className="mt-6">
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.sections.activity')}</CardTitle>
          </CardHeader>
          <CardBody>
            <EmptyState icon={Activity} title={t('dashboard.activity.empty')} />
          </CardBody>
        </Card>
      </div>
    </>
  );
}

function KpiTile({ kpi }: { kpi: Kpi }) {
  const { t } = useTranslation();
  const tone = {
    brand:   'bg-brand-50 text-brand-700',
    success: 'bg-emerald-50 text-emerald-700',
    warning: 'bg-amber-50 text-amber-800',
    info:    'bg-sky-50 text-sky-700',
  }[kpi.tone];

  return (
    <Card>
      <CardBody className="flex items-start justify-between gap-3 p-5">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-500">
            {t(kpi.i18nKey)}
          </p>
          <p className="mt-1.5 text-2xl font-semibold tracking-tight text-ink-900">{kpi.value}</p>
          {kpi.subtitle && (
            <p className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-ink-500">
              {kpi.subtitle}
            </p>
          )}
        </div>
        <span className={'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ' + tone}>
          <kpi.icon size={18} aria-hidden />
        </span>
      </CardBody>
    </Card>
  );
}

function KpiTileSkeleton({ kpi }: { kpi: Pick<Kpi, 'i18nKey' | 'icon' | 'tone'> }) {
  const { t } = useTranslation();
  const tone = {
    brand:   'bg-brand-50 text-brand-700',
    success: 'bg-emerald-50 text-emerald-700',
    warning: 'bg-amber-50 text-amber-800',
    info:    'bg-sky-50 text-sky-700',
  }[kpi.tone];
  return (
    <Card>
      <CardBody className="flex items-start justify-between gap-3 p-5">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-ink-500">{t(kpi.i18nKey)}</p>
          <Skeleton className="mt-2 h-7 w-16" />
        </div>
        <span className={'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ' + tone}>
          <kpi.icon size={18} aria-hidden />
        </span>
      </CardBody>
    </Card>
  );
}

function AttentionRow({
  tone,
  icon: Icon,
  title,
  body,
  to,
}: {
  tone: 'warning' | 'info' | 'danger' | 'success';
  icon: LucideIcon;
  title: string;
  body: string;
  to: string;
}) {
  const cls = {
    warning: 'bg-amber-50 text-amber-700 ring-amber-200',
    info: 'bg-sky-50 text-sky-700 ring-sky-200',
    danger: 'bg-brand-50 text-brand-700 ring-brand-200',
    success: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  }[tone];
  return (
    <Link
      to={to}
      className="flex items-start gap-3 rounded-lg border border-ink-100 p-3 transition-colors hover:border-ink-200 hover:bg-ink-50"
    >
      <span className={'mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md ring-1 ring-inset ' + cls}>
        <Icon size={14} aria-hidden />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-ink-900">{title}</p>
        <p className="text-xs text-ink-500">{body}</p>
      </div>
      <ArrowUpRight size={16} className="mt-1 text-ink-400 rtl:-scale-x-100" />
    </Link>
  );
}

function Shortcut({ to, icon: Icon, label }: { to: string; icon: LucideIcon; label: string }) {
  return (
    <Link
      to={to}
      className="flex flex-col items-center justify-center gap-2 rounded-lg border border-ink-100 p-3 text-center transition-colors hover:border-brand-200 hover:bg-brand-50"
    >
      <span className="flex h-8 w-8 items-center justify-center rounded-md bg-brand-50 text-brand-700">
        <Icon size={16} />
      </span>
      <span className="text-xs font-medium text-ink-700">{label}</span>
    </Link>
  );
}
