import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Search,
  UserPlus,
  Baby,
  Filter,
  Download,
  MoreHorizontal,
  Users,
  ChevronLeft,
  ChevronRight,
  Crown,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { searchPatients, PatientResponse } from './api';
import { cn } from '@/shared/ui/cn';

export function PatientListPage() {
  const { t, i18n } = useTranslation();
  const [searchParams] = useSearchParams();
  const highlight = searchParams.get('highlight');

  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'ADULT' | 'INFANT'>('ALL');
  const [vipOnly, setVipOnly] = useState(false);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['patients', query, page],
    queryFn: () => searchPatients(query, page, 20),
    placeholderData: (prev) => prev,
  });

  const filtered = useMemo(() => {
    if (!data) return undefined;
    let rows = data.content;
    if (typeFilter !== 'ALL') rows = rows.filter((p) => p.type === typeFilter);
    if (vipOnly) rows = rows.filter((p) => p.vip);
    return rows;
  }, [data, typeFilter, vipOnly]);

  const dateFmt = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
      }),
    [i18n.language],
  );

  return (
    <>
      <PageHeader
        title={t('patient.title')}
        description={t('patient.subtitle')}
        actions={
          <>
            <Button variant="secondary" size="md">
              <Download size={14} className="me-1.5" />
              {t('common.export')}
            </Button>
            <Link to="/reception/patients/new">
              <Button variant="primary" size="md">
                <UserPlus size={14} className="me-1.5" />
                {t('patient.register')}
              </Button>
            </Link>
          </>
        }
      />

      <Card>
        <div className="border-b border-ink-100 p-3">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative min-w-[260px] flex-1">
              <Search
                size={14}
                className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400"
              />
              <input
                type="search"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setPage(0);
                }}
                placeholder={t('patient.search') ?? ''}
                className="h-9 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-3 text-sm placeholder:text-ink-400 focus:border-brand-500"
              />
            </div>

            <div className="flex items-center gap-2 text-xs">
              <Filter size={14} className="text-ink-400" />
              <span className="font-medium text-ink-600">{t('common.filters')}:</span>
              <FilterPill active={typeFilter === 'ALL'} onClick={() => setTypeFilter('ALL')}>
                {t('common.all')}
              </FilterPill>
              <FilterPill active={typeFilter === 'ADULT'} onClick={() => setTypeFilter('ADULT')}>
                {t('patient.adult')}
              </FilterPill>
              <FilterPill active={typeFilter === 'INFANT'} onClick={() => setTypeFilter('INFANT')}>
                {t('patient.infant')}
              </FilterPill>
              <span className="mx-1 h-4 w-px bg-ink-200" />
              <FilterPill active={vipOnly} onClick={() => setVipOnly((v) => !v)}>
                <Crown size={11} className="me-1" />
                VIP
              </FilterPill>
            </div>
          </div>
        </div>

        {isLoading ? (
          <TableSkeleton />
        ) : !filtered || filtered.length === 0 ? (
          <EmptyState
            icon={Users}
            title={t('patient.none')}
            description={t('patient.noneHint')}
            action={
              <Link to="/reception/patients/new">
                <Button>
                  <UserPlus size={14} className="me-1.5" />
                  {t('patient.register')}
                </Button>
              </Link>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-ink-100 bg-ink-50/60">
                <tr className="text-start text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                  <Th>{t('patient.mrn')}</Th>
                  <Th>{t('patient.fullName')}</Th>
                  <Th>{t('patient.type')}</Th>
                  <Th>{t('patient.gender')}</Th>
                  <Th>{t('patient.dob')}</Th>
                  <Th>{t('patient.mobile')}</Th>
                  <Th className="w-10 text-end">{t('common.actions')}</Th>
                </tr>
              </thead>
              <tbody className={cn('divide-y divide-ink-100', isFetching && 'opacity-70 transition-opacity')}>
                {filtered.map((p) => (
                  <PatientRow
                    key={p.id}
                    patient={p}
                    highlighted={p.id === highlight}
                    formatDate={(s) => dateFmt.format(new Date(s))}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-ink-100 px-4 py-3 text-sm text-ink-600">
            <span className="text-xs">
              <span className="font-medium text-ink-900">
                {data.number * data.size + 1}–{data.number * data.size + data.content.length}
              </span>{' '}
              of <span className="font-medium text-ink-900">{data.totalElements}</span>
            </span>
            <div className="flex gap-1">
              <Button
                size="sm"
                variant="secondary"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft size={14} className="rtl:rotate-180" />
                {t('common.previous')}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                {t('common.next')}
                <ChevronRight size={14} className="rtl:rotate-180" />
              </Button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}

function PatientRow({
  patient,
  highlighted,
  formatDate,
}: {
  patient: PatientResponse;
  highlighted: boolean;
  formatDate: (iso: string) => string;
}) {
  const { t } = useTranslation();
  return (
    <tr className={cn('group transition-colors hover:bg-ink-50/60', highlighted && 'bg-brand-50/40')}>
      <Td>
        <span className="font-mono text-xs font-semibold text-ink-900">{patient.mrn}</span>
      </Td>
      <Td>
        <div className="flex items-center gap-2">
          <span className="font-medium text-ink-900">{patient.fullName}</span>
          {patient.vip && (
            <Badge tone="brand">
              <Crown size={10} className="me-0.5" />
              {t('patient.vipBadge')}
            </Badge>
          )}
        </div>
        {patient.adult?.address && (
          <div className="mt-0.5 truncate text-xs text-ink-500">{patient.adult.address}</div>
        )}
      </Td>
      <Td>
        {patient.type === 'ADULT' ? (
          <Badge tone="info">{t('patient.adult')}</Badge>
        ) : (
          <Badge tone="warning">
            <Baby size={10} className="me-0.5" />
            {t('patient.infant')}
          </Badge>
        )}
      </Td>
      <Td>
        <span className="text-ink-700">
          {patient.gender === 'MALE' ? t('patient.male') : t('patient.female')}
        </span>
      </Td>
      <Td>
        <span className="text-ink-700">{formatDate(patient.dateOfBirth)}</span>
      </Td>
      <Td>
        <span className="font-mono text-xs text-ink-700">
          {patient.adult?.mobileNumber ?? patient.infant?.motherMobile ?? '—'}
        </span>
      </Td>
      <Td className="text-end">
        <button
          type="button"
          className="rounded-md p-1 text-ink-400 opacity-0 transition-opacity hover:bg-ink-100 hover:text-ink-700 group-hover:opacity-100"
          aria-label={t('common.actions')}
        >
          <MoreHorizontal size={16} />
        </button>
      </Td>
    </tr>
  );
}

function FilterPill({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'inline-flex h-7 items-center rounded-full px-3 text-xs font-medium transition-colors',
        active
          ? 'bg-brand-600 text-white'
          : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
      )}
    >
      {children}
    </button>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn('px-4 py-3 text-start font-semibold', className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 align-middle', className)}>{children}</td>;
}

function TableSkeleton() {
  return (
    <div className="space-y-2 p-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-5 flex-1" />
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-28" />
        </div>
      ))}
    </div>
  );
}
