import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Users,
  Wallet,
  BedDouble,
  ListOrdered,
  ArrowUpRight,
  ArrowDownRight,
  UserPlus,
  CalendarPlus,
  CreditCard,
  Activity,
  Clock,
  CheckCircle2,
  AlertTriangle,
  type LucideIcon,
} from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/shared/ui/Card';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Badge } from '@/shared/ui/Badge';
import { useAuthStore } from '@/shared/auth/authStore';

type Trend = 'up' | 'down' | 'flat';

type Kpi = {
  i18nKey: string;
  value: string;
  delta?: string;
  trend?: Trend;
  icon: LucideIcon;
  tone: 'brand' | 'success' | 'warning' | 'info';
};

// Stub values — wired up once the corresponding backend endpoints land.
const KPIS: Kpi[] = [
  { i18nKey: 'dashboard.kpi.patientsToday',  value: '24', delta: '+8%',  trend: 'up',   icon: Users,        tone: 'brand'   },
  { i18nKey: 'dashboard.kpi.pendingPayments',value: '6',  delta: '−2',   trend: 'down', icon: Wallet,       tone: 'warning' },
  { i18nKey: 'dashboard.kpi.bedsOccupancy',  value: '12 / 20', delta: '60%', trend: 'flat', icon: BedDouble, tone: 'info'    },
  { i18nKey: 'dashboard.kpi.activeQueues',   value: '4',  delta: '0',    trend: 'flat', icon: ListOrdered, tone: 'success' },
];

export function DashboardPage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);

  return (
    <>
      <PageHeader
        title={t('dashboard.welcome', { name: user?.fullName?.split(' ')[1] ?? user?.fullName ?? '' })}
        description={t('dashboard.summary')}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {KPIS.map((k) => (
          <KpiTile key={k.i18nKey} kpi={k} />
        ))}
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle size={16} className="text-amber-500" />
              {t('dashboard.sections.attention')}
            </CardTitle>
            <Badge tone="warning" dot>
              3
            </Badge>
          </CardHeader>
          <CardBody className="space-y-3">
            <AttentionRow
              tone="warning"
              icon={Wallet}
              title={t('dashboard.attention.pendingPaymentsTitle', { count: 6 })}
              body={t('dashboard.attention.pendingPaymentsBody')}
              to="/cashier"
            />
            <AttentionRow
              tone="info"
              icon={Activity}
              title={t('dashboard.attention.labResultsTitle', { count: 4 })}
              body={t('dashboard.attention.labResultsBody')}
              to="/departments/laboratory"
            />
            <AttentionRow
              tone="danger"
              icon={Clock}
              title={t('dashboard.attention.bedExpiryTitle', { count: 2 })}
              body={t('dashboard.attention.bedExpiryBody')}
              to="/departments/emergency"
            />
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
            <ul className="divide-y divide-ink-100">
              <ActivityRow
                tone="success"
                title={t('dashboard.activity.patientRegistered')}
                detail="MRN ALB-2026-000004 · Layla Hassan"
                when="2m ago"
              />
              <ActivityRow
                tone="success"
                title={t('dashboard.activity.paymentApproved')}
                detail="ALB-2026-000003 · IQD 45,000"
                when="9m ago"
              />
              <ActivityRow
                tone="info"
                title={t('dashboard.activity.caseForwarded', { dept: t('nav.laboratory') })}
                detail="Visit V-218 · ordered by Dr. Kareem"
                when="14m ago"
              />
            </ul>
          </CardBody>
        </Card>
      </div>

      <p className="mt-4 px-1 text-xs text-ink-400">{t('dashboard.stub')}</p>
    </>
  );
}

function KpiTile({ kpi }: { kpi: Kpi }) {
  const { t } = useTranslation();
  const TrendIcon = kpi.trend === 'down' ? ArrowDownRight : ArrowUpRight;
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
          {kpi.delta && (
            <p
              className={
                'mt-2 inline-flex items-center gap-1 text-xs font-medium ' +
                (kpi.trend === 'down' ? 'text-brand-700' : kpi.trend === 'up' ? 'text-emerald-700' : 'text-ink-500')
              }
            >
              {kpi.trend !== 'flat' && <TrendIcon size={12} />}
              {kpi.delta}
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

function ActivityRow({
  tone,
  title,
  detail,
  when,
}: {
  tone: 'success' | 'info' | 'warning';
  title: string;
  detail: string;
  when: string;
}) {
  const dotCls = {
    success: 'bg-emerald-500',
    info: 'bg-sky-500',
    warning: 'bg-amber-500',
  }[tone];
  return (
    <li className="flex items-center gap-3 py-3">
      <span className={'h-2 w-2 shrink-0 rounded-full ' + dotCls} />
      {tone === 'success' ? (
        <CheckCircle2 size={14} className="text-emerald-600" />
      ) : (
        <Activity size={14} className="text-sky-600" />
      )}
      <div className="min-w-0 flex-1">
        <p className="text-sm text-ink-900">{title}</p>
        <p className="truncate text-xs text-ink-500">{detail}</p>
      </div>
      <span className="text-xs text-ink-400">{when}</span>
    </li>
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
