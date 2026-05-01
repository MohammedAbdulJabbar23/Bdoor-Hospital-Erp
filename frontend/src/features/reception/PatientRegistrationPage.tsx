import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { z } from 'zod';
import {
  ArrowLeft,
  IdCard,
  Phone,
  ShieldAlert,
  Crown,
  UserPlus,
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';
import { Card, CardBody } from '@/shared/ui/Card';
import { PageHeader } from '@/shared/ui/PageHeader';
import { SectionHeader } from '@/shared/ui/SectionHeader';
import { extractApiError } from '@/shared/api/client';
import { registerAdult, RegisterAdultBody } from './api';

const schema = z.object({
  fullName: z.string().min(1).max(300),
  gender: z.enum(['MALE', 'FEMALE']),
  dateOfBirth: z.string().min(1),
  nationalId: z.string().optional(),
  mobileNumber: z.string().optional(),
  address: z.string().optional(),
  occupation: z.string().optional(),
  emergencyContactName: z.string().optional(),
  emergencyContactMobile: z.string().optional(),
  vip: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export function PatientRegistrationPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { gender: 'MALE', vip: false },
  });

  const mutation = useMutation({
    mutationFn: (body: RegisterAdultBody) => registerAdult(body),
    onSuccess: async (saved) => {
      toast.success(t('patient.success', { mrn: saved.mrn }));
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
      navigate(`/reception/patients?highlight=${saved.id}`);
    },
    onError: (err) => {
      const apiErr = extractApiError(err);
      toast.error(t('patient.error', { message: apiErr?.message ?? 'Failed' }));
    },
  });

  const onSubmit = handleSubmit((values) => mutation.mutate(values));

  return (
    <>
      <PageHeader
        title={t('patient.register')}
        description={t('patient.subtitle')}
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
            <SectionHeader
              icon={IdCard}
              title={t('patient.sections.identity')}
              description={t('patient.sections.identity')}
            />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <Input
                  label={
                    <>
                      {t('patient.fullName')} <span className="text-brand-600">*</span>
                    </>
                  }
                  error={errors.fullName && t('common.required')}
                  {...register('fullName')}
                />
              </div>
              <Select label={t('patient.gender')} {...register('gender')}>
                <option value="MALE">{t('patient.male')}</option>
                <option value="FEMALE">{t('patient.female')}</option>
              </Select>
              <Input
                label={
                  <>
                    {t('patient.dob')} <span className="text-brand-600">*</span>
                  </>
                }
                type="date"
                error={errors.dateOfBirth && t('common.required')}
                {...register('dateOfBirth')}
              />
              <Input label={t('patient.nationalId')} {...register('nationalId')} />
              <Input label={t('patient.occupation')} {...register('occupation')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={Phone} title={t('patient.sections.contact')} />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input label={t('patient.mobile')} {...register('mobileNumber')} />
              <div className="sm:col-span-2">
                <Input label={t('patient.address')} {...register('address')} />
              </div>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={ShieldAlert} title={t('patient.sections.emergency')} />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                label={t('patient.emergencyContactName')}
                {...register('emergencyContactName')}
              />
              <Input
                label={t('patient.emergencyContactMobile')}
                {...register('emergencyContactMobile')}
              />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={Crown} title={t('patient.sections.flags')} />
            <label className="flex items-start gap-3 rounded-lg border border-brand-100 bg-brand-50/50 p-4 transition-colors hover:border-brand-200">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 rounded accent-brand-600"
                {...register('vip')}
              />
              <div>
                <div className="text-sm font-medium text-ink-900">{t('patient.vip')}</div>
                <div className="mt-0.5 text-xs text-ink-500">{t('patient.vipHint')}</div>
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
              <UserPlus size={14} className="me-1.5" />
              {mutation.isPending ? t('common.loading') : t('common.save')}
            </Button>
          </div>
        </div>
      </form>
    </>
  );
}
