import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { Plus, AlertTriangle, ShoppingCart, X, Package, Search } from 'lucide-react';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Skeleton } from '@/shared/ui/Skeleton';
import { EmptyState } from '@/shared/ui/EmptyState';
import { extractApiError } from '@/shared/api/client';
import { listItems, ServiceItem } from '@/features/admin/catalogues/api';
import { searchPatients, PatientResponse } from '@/features/reception/api';
import {
  DrugBatch, DrugStock, listStock, listBatches, listExpiring, receiveBatch,
  createOtcSale, OtcSaleLine,
} from './inventoryApi';
import { cn } from '@/shared/ui/cn';

type Tab = 'stock' | 'expiring' | 'batches';

export function PharmacyInventoryPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('stock');
  const [receiveOpen, setReceiveOpen] = useState(false);
  const [otcOpen, setOtcOpen] = useState(false);

  return (
    <>
      <PageHeader
        title={t('pharmacy.inventoryTitle')}
        description={t('pharmacy.inventoryDescription')}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="secondary" onClick={() => setReceiveOpen(true)}>
              <Plus size={14} className="me-1.5" /> {t('pharmacy.receiveStock')}
            </Button>
            <Button onClick={() => setOtcOpen(true)}>
              <ShoppingCart size={14} className="me-1.5" /> {t('pharmacy.walkinSale')}
            </Button>
          </div>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        <TabBtn active={tab === 'stock'} onClick={() => setTab('stock')}>{t('pharmacy.tabByDrug')}</TabBtn>
        <TabBtn active={tab === 'expiring'} onClick={() => setTab('expiring')}>{t('pharmacy.tabExpiring')}</TabBtn>
        <TabBtn active={tab === 'batches'} onClick={() => setTab('batches')}>{t('pharmacy.tabAllBatches')}</TabBtn>
      </div>

      {tab === 'stock' && <StockByDrugTab />}
      {tab === 'expiring' && <ExpiringTab />}
      {tab === 'batches' && <AllBatchesTab />}

      {receiveOpen && <ReceiveBatchDialog onClose={() => setReceiveOpen(false)} />}
      {otcOpen && <OtcSaleDialog onClose={() => setOtcOpen(false)} />}
    </>
  );
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors',
        active ? 'border-brand-300 bg-brand-50 text-brand-900' : 'border-ink-200 bg-white text-ink-700 hover:bg-ink-50',
      )}
    >
      {children}
    </button>
  );
}

function StockByDrugTab() {
  const { t } = useTranslation();
  const { data, isLoading } = useQuery({ queryKey: ['inv-stock'], queryFn: listStock });
  if (isLoading) return <Skeleton className="h-64" />;
  if (!data || data.length === 0) {
    return <EmptyState icon={Package} title={t('pharmacy.noStock')} description={t('pharmacy.noStockHint')} />;
  }
  return (
    <Card>
      <table className="w-full text-sm">
        <thead className="border-b border-ink-100 bg-ink-50/40 text-[11px] uppercase tracking-wide text-ink-500">
          <tr>
            <th className="px-4 py-3 text-start">{t('pharmacy.colDrug')}</th>
            <th className="px-4 py-3 text-start">{t('pharmacy.colCode')}</th>
            <th className="px-4 py-3 text-end">{t('pharmacy.colTotalInStock')}</th>
            <th className="px-4 py-3 text-start">{t('pharmacy.colEarliestExpiry')}</th>
            <th className="px-4 py-3 text-end">{t('pharmacy.colBatches')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {data.map((r: DrugStock) => (
            <tr key={r.drugServiceItemId} className="hover:bg-ink-50/40">
              <td className="px-4 py-2.5 font-medium text-ink-900">{r.drugName ?? '—'}</td>
              <td className="px-4 py-2.5 font-mono text-[11px] text-ink-500">{r.drugCode ?? '—'}</td>
              <td className="px-4 py-2.5 text-end font-mono">{r.totalRemaining}</td>
              <td className="px-4 py-2.5">
                {r.earliestExpiry ? <ExpiryBadge date={r.earliestExpiry} /> : <span className="text-ink-400">—</span>}
              </td>
              <td className="px-4 py-2.5 text-end">{r.batchCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

function ExpiringTab() {
  const { t } = useTranslation();
  const [days, setDays] = useState(60);
  const { data, isLoading } = useQuery({
    queryKey: ['inv-expiring', days],
    queryFn: () => listExpiring(days),
  });
  return (
    <>
      <div className="mb-3 flex items-center gap-2 text-sm">
        <span className="text-ink-500">{t('pharmacy.showExpiringWithin')}</span>
        <input
          type="number" min={7} max={365} value={days}
          onChange={(e) => setDays(Number(e.target.value) || 60)}
          className="h-8 w-20 rounded-md border border-ink-200 bg-white px-2 text-sm focus:border-brand-500"
        />
        <span className="text-ink-500">{t('pharmacy.days')}</span>
      </div>
      {isLoading ? (
        <Skeleton className="h-64" />
      ) : !data || data.length === 0 ? (
        <EmptyState icon={AlertTriangle} title={t('pharmacy.noExpiring')} description={t('pharmacy.noExpiringHint', { days })} />
      ) : (
        <BatchTable batches={data} />
      )}
    </>
  );
}

function AllBatchesTab() {
  const { t } = useTranslation();
  const { data, isLoading } = useQuery({ queryKey: ['inv-batches'], queryFn: () => listBatches() });
  if (isLoading) return <Skeleton className="h-64" />;
  if (!data || data.length === 0) {
    return <EmptyState icon={Package} title={t('pharmacy.noBatches')} />;
  }
  return <BatchTable batches={data} />;
}

function BatchTable({ batches }: { batches: DrugBatch[] }) {
  const { t } = useTranslation();
  return (
    <Card>
      <table className="w-full text-sm">
        <thead className="border-b border-ink-100 bg-ink-50/40 text-[11px] uppercase tracking-wide text-ink-500">
          <tr>
            <th className="px-4 py-3 text-start">{t('pharmacy.colDrug')}</th>
            <th className="px-4 py-3 text-start">{t('pharmacy.colBatchNo')}</th>
            <th className="px-4 py-3 text-start">{t('pharmacy.colExpiry')}</th>
            <th className="px-4 py-3 text-end">{t('pharmacy.colRemaining')}</th>
            <th className="px-4 py-3 text-end">{t('pharmacy.colReceived')}</th>
            <th className="px-4 py-3 text-start">{t('pharmacy.colSupplier')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {batches.map((b) => (
            <tr key={b.id} className="hover:bg-ink-50/40">
              <td className="px-4 py-2.5">
                <div className="font-medium text-ink-900">{b.drugName ?? '—'}</div>
                <div className="font-mono text-[10px] text-ink-500">{b.drugCode ?? ''}</div>
              </td>
              <td className="px-4 py-2.5 font-mono">{b.batchNo}</td>
              <td className="px-4 py-2.5"><ExpiryBadge date={b.expiryDate} /></td>
              <td className={cn('px-4 py-2.5 text-end font-mono',
                b.qtyRemaining === 0 ? 'text-ink-400' : 'text-ink-900')}>
                {b.qtyRemaining} / {b.qtyReceived}
              </td>
              <td className="px-4 py-2.5 text-end">{b.qtyReceived}</td>
              <td className="px-4 py-2.5 text-xs text-ink-700">{b.supplier ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

function ExpiryBadge({ date }: { date: string }) {
  const { t } = useTranslation();
  const today = new Date();
  const exp = new Date(date);
  const days = Math.ceil((exp.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
  const tone: 'success' | 'warning' | 'danger' = days < 0 ? 'danger' : days < 30 ? 'danger' : days < 90 ? 'warning' : 'success';
  const label = days < 0 ? t('pharmacy.expiredAgo', { days: -days }) : days < 60 ? t('pharmacy.daysLeft', { days }) : exp.toLocaleDateString();
  return <Badge tone={tone}>{label}</Badge>;
}

function ReceiveBatchDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: drugs, isLoading: drugsLoading } = useQuery({
    queryKey: ['catalogue-items', 'DRUG'],
    queryFn: () => listItems('DRUG', true),
  });
  const [drugId, setDrugId] = useState<string>('');
  const [batchNo, setBatchNo] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [qty, setQty] = useState<string>('');
  const [unitCost, setUnitCost] = useState<string>('');
  const [supplier, setSupplier] = useState('');

  const mut = useMutation({
    mutationFn: () => receiveBatch({
      drugServiceItemId: drugId, batchNo, expiryDate,
      qty: Number(qty),
      unitCost: unitCost ? Number(unitCost) : undefined,
      supplier: supplier || undefined,
    }),
    onSuccess: async () => {
      toast.success(t('pharmacy.receivedUnits', { qty }));
      await queryClient.invalidateQueries({ queryKey: ['inv-stock'] });
      await queryClient.invalidateQueries({ queryKey: ['inv-batches'] });
      await queryClient.invalidateQueries({ queryKey: ['inv-expiring'] });
      onClose();
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? t('pharmacy.receiveFailed')),
  });

  return (
    <DialogShell title={t('pharmacy.receiveDialogTitle')} onClose={onClose}>
      <div className="space-y-3 p-5">
        <label className="block">
          <span className="mb-1.5 block text-xs font-medium text-ink-700">{t('pharmacy.drug')}</span>
          <select
            value={drugId} onChange={(e) => setDrugId(e.target.value)}
            className="h-10 w-full rounded-lg border border-ink-200 bg-white px-3 text-sm focus:border-brand-500"
          >
            <option value="">{drugsLoading ? t('pharmacy.loading') : t('pharmacy.pickADrug')}</option>
            {(drugs ?? []).map((d: ServiceItem) => (
              <option key={d.id} value={d.id}>{d.nameEn} ({d.code})</option>
            ))}
          </select>
        </label>
        <div className="grid grid-cols-2 gap-3">
          <Input label={t('pharmacy.batchNumber')} value={batchNo} onChange={(e) => setBatchNo(e.target.value)} />
          <Input label={t('pharmacy.expiryDate')} type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} />
          <Input label={t('pharmacy.quantity')} type="number" min={1} value={qty} onChange={(e) => setQty(e.target.value)} />
          <Input label={t('pharmacy.unitCost')} type="number" min={0} value={unitCost} onChange={(e) => setUnitCost(e.target.value)} />
          <div className="col-span-2">
            <Input label={t('pharmacy.supplier')} value={supplier} onChange={(e) => setSupplier(e.target.value)} />
          </div>
        </div>
      </div>
      <div className="flex items-center justify-end gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
        <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
        <Button
          onClick={() => mut.mutate()}
          disabled={!drugId || !batchNo || !expiryDate || !qty || mut.isPending}
        >
          {mut.isPending ? t('pharmacy.receiving') : t('pharmacy.receiveStock')}
        </Button>
      </div>
    </DialogShell>
  );
}

function OtcSaleDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [picked, setPicked] = useState<PatientResponse | null>(null);
  const [lines, setLines] = useState<OtcSaleLine[]>([]);

  const { data: matches } = useQuery({
    queryKey: ['otc-patients', query],
    queryFn: () => searchPatients(query, 0, 8),
    enabled: query.length >= 2 && !picked,
  });
  const { data: drugs } = useQuery({
    queryKey: ['catalogue-items', 'DRUG'],
    queryFn: () => listItems('DRUG', true),
  });

  const total = useMemo(() => {
    const map = new Map((drugs ?? []).map((d: ServiceItem) => [d.id, d.fee ?? 0] as const));
    return lines.reduce((sum, l) => sum + (map.get(l.drugServiceItemId) ?? 0) * l.quantity, 0);
  }, [lines, drugs]);

  const mut = useMutation({
    mutationFn: () => createOtcSale(picked!.id, lines),
    onSuccess: async () => {
      toast.success(t('pharmacy.otcRouted'));
      await queryClient.invalidateQueries({ queryKey: ['pharmacy-dispenses'] });
      await queryClient.invalidateQueries({ queryKey: ['inv-stock'] });
      onClose();
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? t('pharmacy.otcFailed')),
  });

  const addLine = (drugId: string) => {
    const existing = lines.find((l) => l.drugServiceItemId === drugId);
    if (existing) {
      setLines(lines.map((l) => l.drugServiceItemId === drugId ? { ...l, quantity: l.quantity + 1 } : l));
    } else {
      setLines([...lines, { drugServiceItemId: drugId, quantity: 1 }]);
    }
  };
  const removeLine = (drugId: string) => setLines(lines.filter((l) => l.drugServiceItemId !== drugId));
  const setQty = (drugId: string, q: number) => setLines(lines.map((l) =>
    l.drugServiceItemId === drugId ? { ...l, quantity: Math.max(1, q) } : l));

  const drugById = useMemo(() => new Map((drugs ?? []).map((d: ServiceItem) => [d.id, d] as const)), [drugs]);

  return (
    <DialogShell title={t('pharmacy.otcDialogTitle')} onClose={onClose}>
      <div className="space-y-4 p-5">
        {/* Patient picker */}
        {!picked ? (
          <div>
            <div className="relative">
              <Search size={14} className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 text-ink-400" />
              <input
                type="search"
                autoFocus
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={t('pharmacy.searchPatient')}
                className="h-10 w-full rounded-lg border border-ink-200 bg-white ps-9 pe-3 text-sm placeholder:text-ink-400 focus:border-brand-500"
              />
            </div>
            {matches && matches.content.length > 0 && (
              <ul className="mt-2 max-h-48 space-y-1 overflow-y-auto rounded-lg border border-ink-100">
                {matches.content.map((p) => (
                  <li key={p.id}>
                    <button type="button" onClick={() => setPicked(p)}
                      className="w-full rounded-md px-3 py-2 text-start text-sm hover:bg-ink-50">
                      <div className="flex items-center justify-between">
                        <div>
                          <div className="font-medium text-ink-900">{p.fullName}</div>
                          <div className="font-mono text-[11px] text-ink-500">{p.mrn}</div>
                        </div>
                        {p.vip && <Badge tone="brand">VIP</Badge>}
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : (
          <div className="flex items-center justify-between rounded-lg border border-ink-100 bg-ink-50/50 p-3">
            <div>
              <div className="font-medium text-ink-900">{picked.fullName}</div>
              <div className="font-mono text-[11px] text-ink-500">{picked.mrn}</div>
            </div>
            <button type="button" onClick={() => setPicked(null)} className="text-xs text-ink-500 hover:underline">
              {t('pharmacy.change')}
            </button>
          </div>
        )}

        {/* Drug picker */}
        {picked && (
          <div>
            <div className="mb-1 text-xs font-medium text-ink-700">{t('pharmacy.drugsCatalogue')}</div>
            <select
              onChange={(e) => {
                if (e.target.value) addLine(e.target.value);
                e.target.value = '';
              }}
              className="h-10 w-full rounded-lg border border-ink-200 bg-white px-3 text-sm focus:border-brand-500"
            >
              <option value="">{t('pharmacy.addADrug')}</option>
              {(drugs ?? []).map((d: ServiceItem) => (
                <option key={d.id} value={d.id}>{d.nameEn} ({d.code}) — {(d.fee ?? 0).toLocaleString()} {d.currency}</option>
              ))}
            </select>

            {lines.length > 0 && (
              <ul className="mt-3 divide-y divide-ink-100 rounded-lg border border-ink-100">
                {lines.map((l) => {
                  const drug = drugById.get(l.drugServiceItemId);
                  const fee = drug?.fee ?? 0;
                  return (
                    <li key={l.drugServiceItemId} className="flex items-center gap-3 px-3 py-2">
                      <div className="flex-1">
                        <div className="text-sm text-ink-900">{drug?.nameEn}</div>
                        <div className="font-mono text-[10px] text-ink-500">{drug?.code}</div>
                      </div>
                      <input
                        type="number" min={1} value={l.quantity}
                        onChange={(e) => setQty(l.drugServiceItemId, Number(e.target.value))}
                        className="h-8 w-16 rounded-md border border-ink-200 px-2 text-sm"
                      />
                      <span className="w-20 text-end font-mono text-sm">{(fee * l.quantity).toLocaleString()}</span>
                      <button type="button" onClick={() => removeLine(l.drugServiceItemId)}
                        className="rounded p-1 text-ink-400 hover:bg-brand-50 hover:text-brand-700">
                        <X size={14} />
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </div>
      <div className="flex items-center justify-between gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
        <div className="text-sm">
          <span className="text-ink-500">{t('pharmacy.total')}</span>
          <span className="font-mono text-base font-semibold text-ink-900">{total.toLocaleString()}</span>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button
            onClick={() => mut.mutate()}
            disabled={!picked || lines.length === 0 || mut.isPending}
          >
            {mut.isPending ? t('pharmacy.routingToCashier') : t('pharmacy.sendToCashier')}
          </Button>
        </div>
      </div>
    </DialogShell>
  );
}

function DialogShell({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="flex max-h-[90vh] w-full max-w-xl flex-col overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <h2 className="text-base font-semibold text-ink-900">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {children}
        </div>
      </div>
    </div>
  );
}
