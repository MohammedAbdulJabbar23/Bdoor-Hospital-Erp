import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  FlaskConical,
  Scan,
  HeartPulse,
  Siren,
  Pill,
  Plus,
  Pencil,
  Archive,
  ArchiveRestore,
  ArrowRight,
  type LucideIcon,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { cn } from '@/shared/ui/cn';
import {
  listItems,
  archiveItem,
  unarchiveItem,
  ServiceCategory,
  ServiceItem,
} from './api';
import { CatalogueItemDialog } from './CatalogueItemDialog';

const TABS: { category: ServiceCategory; icon: LucideIcon }[] = [
  { category: 'LAB',       icon: FlaskConical },
  { category: 'IMAGING',   icon: Scan         },
  { category: 'ECO',       icon: HeartPulse   },
  { category: 'EMERGENCY', icon: Siren        },
  { category: 'DRUG',      icon: Pill         },
];

export function CataloguesPage() {
  const { t, i18n } = useTranslation();
  const [active, setActive] = useState<ServiceCategory>('LAB');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ServiceItem | null>(null);
  const queryClient = useQueryClient();

  const { data: items, isLoading } = useQuery({
    queryKey: ['catalogue', active],
    queryFn: () => listItems(active),
  });

  const archiveMut = useMutation({
    mutationFn: (id: string) => archiveItem(id),
    onSuccess: async () => {
      toast.success(t('catalogues.archivedToast'));
      await queryClient.invalidateQueries({ queryKey: ['catalogue', active] });
    },
  });
  const unarchiveMut = useMutation({
    mutationFn: (id: string) => unarchiveItem(id),
    onSuccess: async () => {
      toast.success(t('catalogues.restoredToast'));
      await queryClient.invalidateQueries({ queryKey: ['catalogue', active] });
    },
  });

  const tab = TABS.find((x) => x.category === active)!;

  const formatCurrency = (n: number, ccy: string) =>
    new Intl.NumberFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-US', {
      style: 'currency',
      currency: ccy || 'IQD',
      maximumFractionDigits: 0,
    }).format(n);

  return (
    <>
      <PageHeader
        title={t('catalogues.title')}
        description={t('catalogues.description')}
        actions={
          <Button
            onClick={() => {
              setEditing(null);
              setDialogOpen(true);
            }}
          >
            <Plus size={14} className="me-1.5" />
            {t('catalogues.newItem')}
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2 rounded-xl border border-ink-200 bg-white p-2 shadow-card">
        {TABS.map((x) => {
          const Icon = x.icon;
          const isActive = x.category === active;
          return (
            <button
              key={x.category}
              type="button"
              onClick={() => setActive(x.category)}
              className={cn(
                'inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-brand-50 text-brand-700 ring-1 ring-inset ring-brand-200'
                  : 'text-ink-600 hover:bg-ink-50',
              )}
            >
              <Icon size={14} />
              {t(`catalogues.tab.${x.category}`)}
            </button>
          );
        })}
      </div>

      <Card>
        <div className="border-b border-ink-100 px-5 py-4">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
              <tab.icon size={18} />
            </span>
            <div>
              <h3 className="text-sm font-semibold text-ink-900">{t('catalogues.catalogueOf', { name: t(`catalogues.tab.${tab.category}`) })}</h3>
              <p className="text-xs text-ink-500">
                {items ? t('catalogues.itemCount', { count: items.length }) : ''}
                {items && items.some((i) => i.forwardTo)
                  ? t('catalogues.forwardsHint')
                  : ''}
              </p>
            </div>
          </div>
        </div>

        {isLoading ? (
          <TableSkeleton />
        ) : !items || items.length === 0 ? (
          <EmptyState
            icon={tab.icon}
            title={t('catalogues.noItems')}
            description={t('catalogues.noItemsHint')}
            action={
              <Button onClick={() => { setEditing(null); setDialogOpen(true); }}>
                <Plus size={14} className="me-1.5" />
                {t('catalogues.addItem')}
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-ink-100 bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                <tr>
                  <Th>{t('catalogues.colCode')}</Th>
                  <Th>{t('catalogues.colName')}</Th>
                  <Th>{t('catalogues.colFee')}</Th>
                  <Th>{t('catalogues.colStatus')}</Th>
                  <Th className="w-32 text-end">{t('common.actions')}</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {items.map((it) => (
                  <tr key={it.id} className="group transition-colors hover:bg-ink-50/60">
                    <Td>
                      <span className="font-mono text-xs font-semibold text-ink-900">{it.code}</span>
                    </Td>
                    <Td>
                      <div className="font-medium text-ink-900">{it.nameEn}</div>
                      {it.nameAr && (
                        <div className="text-xs text-ink-500" dir="rtl">{it.nameAr}</div>
                      )}
                    </Td>
                    <Td>
                      {it.forwardTo ? (
                        <Badge tone="info">
                          <ArrowRight size={10} className="me-0.5 rtl:rotate-180" />
                          {it.forwardTo}
                        </Badge>
                      ) : it.fee != null ? (
                        <span className="font-mono text-ink-700">
                          {formatCurrency(it.fee, it.currency)}
                        </span>
                      ) : (
                        <span className="text-ink-400">—</span>
                      )}
                    </Td>
                    <Td>
                      {it.active ? (
                        <Badge tone="success" dot>{t('catalogues.active')}</Badge>
                      ) : (
                        <Badge tone="neutral" dot>{t('catalogues.archived')}</Badge>
                      )}
                    </Td>
                    <Td className="text-end">
                      <div className="inline-flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                        <button
                          type="button"
                          onClick={() => { setEditing(it); setDialogOpen(true); }}
                          className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100 hover:text-ink-900"
                          aria-label={t('common.edit')}
                          title={t('common.edit')}
                        >
                          <Pencil size={14} />
                        </button>
                        {it.active ? (
                          <button
                            type="button"
                            onClick={() => archiveMut.mutate(it.id)}
                            className="rounded-md p-1.5 text-ink-500 hover:bg-amber-50 hover:text-amber-700"
                            aria-label={t('catalogues.archived')}
                            title={t('catalogues.archived')}
                          >
                            <Archive size={14} />
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => unarchiveMut.mutate(it.id)}
                            className="rounded-md p-1.5 text-ink-500 hover:bg-emerald-50 hover:text-emerald-700"
                            aria-label={t('catalogues.restoredToast')}
                            title={t('catalogues.restoredToast')}
                          >
                            <ArchiveRestore size={14} />
                          </button>
                        )}
                      </div>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <CatalogueItemDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        category={active}
        editing={editing}
      />
    </>
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
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-20" />
        </div>
      ))}
    </div>
  );
}
