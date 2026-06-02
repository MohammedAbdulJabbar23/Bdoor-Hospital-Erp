import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { z } from 'zod';
import { X } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { extractApiError } from '@/shared/api/client';
import {
  addItem,
  updateItem,
  ServiceCategory,
  ServiceItem,
} from './api';

const schema = z.object({
  code: z.string().min(1).max(50),
  nameEn: z.string().min(1).max(300),
  nameAr: z.string().max(300).optional(),
  description: z.string().max(1000).optional(),
  fee: z.coerce.number().min(0).nullable().optional(),
  currency: z.string().max(10).optional(),
  sortOrder: z.coerce.number().int().min(0).optional(),
  forwardTo: z.string().optional(),
  drugGenericName: z.string().optional(),
  drugDosageForm: z.string().optional(),
  drugStrength: z.string().optional(),
  drugUnit: z.string().optional(),
  drugControlled: z.boolean().optional(),
});
type FormValues = z.infer<typeof schema>;

const CATEGORIES: ServiceCategory[] = ['LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG'];

export function CatalogueItemDialog({
  open,
  onClose,
  category,
  editing,
}: {
  open: boolean;
  onClose: () => void;
  category: ServiceCategory;
  editing: ServiceItem | null;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: '',
      nameEn: '',
      nameAr: '',
      currency: 'IQD',
      sortOrder: 0,
      drugControlled: false,
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        code: editing?.code ?? '',
        nameEn: editing?.nameEn ?? '',
        nameAr: editing?.nameAr ?? '',
        description: editing?.description ?? '',
        fee: editing?.fee ?? undefined,
        currency: editing?.currency ?? 'IQD',
        sortOrder: editing?.sortOrder ?? 0,
        forwardTo: editing?.forwardTo ?? '',
        drugGenericName: editing?.drug?.genericName ?? '',
        drugDosageForm: editing?.drug?.dosageForm ?? '',
        drugStrength: editing?.drug?.strength ?? '',
        drugUnit: editing?.drug?.unit ?? '',
        drugControlled: editing?.drug?.controlled ?? false,
      });
    }
  }, [open, editing, reset]);

  const forwardTo = watch('forwardTo');
  const isDrug = category === 'DRUG';

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const drug = isDrug
        ? {
            genericName: values.drugGenericName || undefined,
            dosageForm: values.drugDosageForm || undefined,
            strength: values.drugStrength || undefined,
            unit: values.drugUnit || undefined,
            controlled: !!values.drugControlled,
          }
        : undefined;

      const fwd = (values.forwardTo || null) as ServiceCategory | null;

      if (editing) {
        return updateItem(editing.id, {
          nameEn: values.nameEn,
          nameAr: values.nameAr || undefined,
          description: values.description || undefined,
          fee: fwd ? undefined : (values.fee ?? undefined),
          currency: values.currency || undefined,
          sortOrder: values.sortOrder,
          forwardTo: fwd,
          drug,
        });
      }
      return addItem({
        category,
        code: values.code,
        nameEn: values.nameEn,
        nameAr: values.nameAr || undefined,
        description: values.description || undefined,
        fee: fwd ? undefined : (values.fee ?? undefined),
        currency: values.currency || undefined,
        sortOrder: values.sortOrder,
        forwardTo: fwd,
        drug,
      });
    },
    onSuccess: async (saved) => {
      toast.success(saved.code);
      await queryClient.invalidateQueries({ queryKey: ['catalogue', category] });
      onClose();
    },
    onError: (err) => {
      const apiErr = extractApiError(err);
      toast.error(apiErr?.message ?? t('catalogues.saveFailed'));
    },
  });

  const onSubmit = handleSubmit((values) => mutation.mutate(values));

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-2xl overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">
              {editing ? `${t('common.edit')} — ${editing.code}` : t('catalogues.newItemTitle', { category })}
            </h2>
            <p className="mt-0.5 text-xs text-ink-500">
              {isDrug ? t('catalogues.drugEntry') : t('catalogues.serviceEntry')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100"
            aria-label={t('common.close')}
          >
            <X size={18} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="max-h-[70vh] space-y-4 overflow-y-auto p-5">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              label={t('catalogues.code')}
              disabled={!!editing}
              error={errors.code && t('common.required')}
              {...register('code')}
            />
            <Input label={t('catalogues.sortOrder')} type="number" {...register('sortOrder')} />
            <Input
              label={t('catalogues.nameEn')}
              error={errors.nameEn && t('common.required')}
              {...register('nameEn')}
            />
            <Input label={t('catalogues.nameAr')} dir="rtl" {...register('nameAr')} />
          </div>

          <Input label={t('catalogues.fieldDescription')} {...register('description')} />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Input
              label={t('catalogues.fee')}
              type="number"
              step="0.01"
              disabled={!!forwardTo}
              hint={forwardTo ? t('catalogues.feeDisabledHint') : undefined}
              {...register('fee')}
            />
            <Input label={t('catalogues.currency')} {...register('currency')} />
            <Select label={t('catalogues.forwardsTo')} {...register('forwardTo')}>
              <option value="">{t('common.none')}</option>
              {CATEGORIES.filter((c) => c !== category).map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
          </div>

          {isDrug && (
            <div className="rounded-lg border border-ink-100 bg-ink-50/40 p-4">
              <h4 className="mb-3 text-xs font-semibold uppercase tracking-wider text-ink-500">
                {t('catalogues.drugDetails')}
              </h4>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Input label={t('catalogues.genericName')} {...register('drugGenericName')} />
                <Input label={t('catalogues.dosageForm')} placeholder={t('catalogues.dosageFormPlaceholder')} {...register('drugDosageForm')} />
                <Input label={t('catalogues.strength')} placeholder={t('catalogues.strengthPlaceholder')} {...register('drugStrength')} />
                <Input label={t('catalogues.unit')} placeholder={t('catalogues.unitPlaceholder')} {...register('drugUnit')} />
                <label className="flex items-center gap-2 text-sm sm:col-span-2">
                  <input type="checkbox" className="h-4 w-4 rounded accent-brand-600" {...register('drugControlled')} />
                  {t('catalogues.controlled')}
                </label>
              </div>
            </div>
          )}
        </form>

        <div className="flex items-center justify-end gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="button" onClick={onSubmit} disabled={isSubmitting || mutation.isPending}>
            {mutation.isPending ? t('common.loading') : t('common.save')}
          </Button>
        </div>
      </div>
    </div>
  );
}
