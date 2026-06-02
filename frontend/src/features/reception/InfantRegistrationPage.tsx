import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { z } from 'zod';
import { ArrowLeft, Baby, HeartPulse, ShieldAlert, Crown } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { Card, CardBody } from '@/shared/ui/Card';
import { PageHeader } from '@/shared/ui/PageHeader';
import { SectionHeader } from '@/shared/ui/SectionHeader';
import { extractApiError } from '@/shared/api/client';
import { registerInfant, RegisterInfantBody } from './api';

const schema = z.object({
  fullName: z.string().max(300).optional(),
  gender: z.enum(['MALE', 'FEMALE']),
  dateOfBirth: z.string().min(1),
  dobTime: z.string().optional(),
  motherName: z.string().optional(),
  motherNationalId: z.string().optional(),
  motherMobile: z.string().optional(),
  fatherName: z.string().optional(),
  fatherMobile: z.string().optional(),
  placeOfBirth: z.enum(['THIS_HOSPITAL', 'OUTSIDE', 'OTHER']),
  deliveryType: z.enum(['VAGINAL', 'C_SECTION', 'ASSISTED']),
  apgar1Min: z.coerce.number().int().min(0).max(10).optional().or(z.literal('').transform(() => undefined)),
  apgar5Min: z.coerce.number().int().min(0).max(10).optional().or(z.literal('').transform(() => undefined)),
  birthWeightKg: z.string().optional(),
  lengthCm: z.string().optional(),
  ofcCm: z.string().optional(),
  gestationalAgeWeeks: z.coerce.number().int().min(20).optional().or(z.literal('').transform(() => undefined)),
  gestationalAgeDays: z.coerce.number().int().min(0).max(6).optional().or(z.literal('').transform(() => undefined)),
  guardianName: z.string().min(1),
  guardianRelationship: z.string().optional(),
  guardianMobile: z.string().optional(),
  guardianNationalId: z.string().optional(),
  vip: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export function InfantRegistrationPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      gender: 'MALE',
      placeOfBirth: 'THIS_HOSPITAL',
      deliveryType: 'VAGINAL',
      vip: false,
    },
  });

  const mutation = useMutation({
    mutationFn: (body: RegisterInfantBody) => registerInfant(body),
    onSuccess: async (saved) => {
      toast.success(t('infant.registered', { mrn: saved.mrn }));
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
      navigate(`/reception/patients?highlight=${saved.id}`);
    },
    onError: (err) => {
      const apiErr = extractApiError(err);
      toast.error(t('infant.error', { message: apiErr?.message ?? t('infant.failed') }));
    },
  });

  const onSubmit = handleSubmit((values) =>
    mutation.mutate({
      ...values,
      // No national ID at birth — Iraqi infants register later.
    } as RegisterInfantBody),
  );

  return (
    <>
      <PageHeader
        title={t('infant.title')}
        description={t('infant.description')}
        actions={
          <Link to="/reception/patients">
            <Button variant="ghost" size="md">
              <ArrowLeft size={14} className="me-1.5 rtl:rotate-180" />
              {t('common.back')}
            </Button>
          </Link>
        }
      />

      <form onSubmit={onSubmit} className="space-y-5 pb-24">
        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={Baby} title={t('infant.sectionIdentity')} />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input label={t('infant.fullName')} {...register('fullName')} />
              <Select label={t('infant.gender')} {...register('gender')}>
                <option value="MALE">{t('infant.male')}</option>
                <option value="FEMALE">{t('infant.female')}</option>
              </Select>
              <Input
                label={<>{t('infant.dob')} <span className="text-brand-600">*</span></>}
                type="date"
                error={errors.dateOfBirth && t('infant.required')}
                {...register('dateOfBirth')}
              />
              <Input label={t('infant.dobTime')} type="time" {...register('dobTime')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={HeartPulse} title={t('infant.sectionDelivery')} />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Select label={t('infant.placeOfBirth')} {...register('placeOfBirth')}>
                <option value="THIS_HOSPITAL">{t('infant.placeThisHospital')}</option>
                <option value="OUTSIDE">{t('infant.placeOutside')}</option>
                <option value="OTHER">{t('infant.placeOther')}</option>
              </Select>
              <Select label={t('infant.deliveryType')} {...register('deliveryType')}>
                <option value="VAGINAL">{t('infant.deliveryVaginal')}</option>
                <option value="C_SECTION">{t('infant.deliveryCSection')}</option>
                <option value="ASSISTED">{t('infant.deliveryAssisted')}</option>
              </Select>
              <Input label={t('infant.gaWeeks')} type="number" min={20} {...register('gestationalAgeWeeks')} />
              <Input label={t('infant.gaDays')} type="number" min={0} max={6} {...register('gestationalAgeDays')} />
              <Input label={t('infant.apgar1')} type="number" min={0} max={10} {...register('apgar1Min')} />
              <Input label={t('infant.apgar5')} type="number" min={0} max={10} {...register('apgar5Min')} />
              <Input label={t('infant.birthWeight')} {...register('birthWeightKg')} />
              <Input label={t('infant.length')} {...register('lengthCm')} />
              <Input label={t('infant.ofc')} {...register('ofcCm')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={ShieldAlert} title={t('infant.sectionParents')} description={t('infant.parentsHint')} />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input label={t('infant.motherName')} {...register('motherName')} />
              <Input label={t('infant.motherMobile')} {...register('motherMobile')} />
              <Input label={t('infant.motherNationalId')} {...register('motherNationalId')} />
              <Input label={t('infant.fatherName')} {...register('fatherName')} />
              <Input label={t('infant.fatherMobile')} {...register('fatherMobile')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={ShieldAlert} title={t('infant.sectionGuardian')} />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                label={<>{t('infant.guardianName')} <span className="text-brand-600">*</span></>}
                error={errors.guardianName && t('infant.required')}
                {...register('guardianName')}
              />
              <Input label={t('infant.guardianRelationship')} placeholder={t('infant.guardianRelationshipPlaceholder')} {...register('guardianRelationship')} />
              <Input label={t('infant.guardianMobile')} {...register('guardianMobile')} />
              <Input label={t('infant.guardianNationalId')} {...register('guardianNationalId')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={Crown} title={t('infant.sectionFlags')} />
            <label className="flex items-start gap-3 rounded-lg border border-brand-100 bg-brand-50/50 p-4 transition-colors hover:border-brand-200">
              <input type="checkbox" className="mt-0.5 h-4 w-4 rounded accent-brand-600" {...register('vip')} />
              <div>
                <div className="text-sm font-medium text-ink-900">{t('infant.vip')}</div>
                <div className="mt-0.5 text-xs text-ink-500">{t('infant.vipHint')}</div>
              </div>
            </label>
          </CardBody>
        </Card>

        <div className="fixed inset-x-0 bottom-0 z-10 border-t border-ink-200 bg-white/95 px-6 py-3 backdrop-blur ltr:ps-[252px] rtl:pe-[252px]">
          <div className="mx-auto flex max-w-[1400px] items-center justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => navigate('/reception/patients')}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              <Baby size={14} className="me-1.5" />
              {mutation.isPending ? t('common.loading') : t('infant.register')}
            </Button>
          </div>
        </div>
      </form>
    </>
  );
}
