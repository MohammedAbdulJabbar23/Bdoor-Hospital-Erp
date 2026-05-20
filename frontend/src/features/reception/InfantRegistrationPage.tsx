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
      toast.success(`Infant ${saved.mrn} registered`);
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
      navigate(`/reception/patients?highlight=${saved.id}`);
    },
    onError: (err) => {
      const apiErr = extractApiError(err);
      toast.error(`Could not register infant: ${apiErr?.message ?? 'Failed'}`);
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
        title="Register infant"
        description="Newborn intake — distinct from adult registration. No national ID required at birth; mother MRN, Apgar, and delivery details are captured."
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
            <SectionHeader icon={Baby} title="Newborn identity" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input label="Full name (optional — defaults to ‘Baby <family>’)" {...register('fullName')} />
              <Select label="Gender" {...register('gender')}>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
              </Select>
              <Input
                label={<>Date of birth <span className="text-brand-600">*</span></>}
                type="date"
                error={errors.dateOfBirth && 'Required'}
                {...register('dateOfBirth')}
              />
              <Input label="Time of birth (optional)" type="time" {...register('dobTime')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={HeartPulse} title="Delivery details" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Select label="Place of birth" {...register('placeOfBirth')}>
                <option value="THIS_HOSPITAL">This hospital</option>
                <option value="OUTSIDE">Outside</option>
                <option value="OTHER">Other</option>
              </Select>
              <Select label="Delivery type" {...register('deliveryType')}>
                <option value="VAGINAL">Vaginal</option>
                <option value="C_SECTION">C-section</option>
                <option value="ASSISTED">Assisted</option>
              </Select>
              <Input label="Gestational age (weeks)" type="number" min={20} {...register('gestationalAgeWeeks')} />
              <Input label="Gestational age (days)" type="number" min={0} max={6} {...register('gestationalAgeDays')} />
              <Input label="Apgar (1 min)" type="number" min={0} max={10} {...register('apgar1Min')} />
              <Input label="Apgar (5 min)" type="number" min={0} max={10} {...register('apgar5Min')} />
              <Input label="Birth weight (kg)" {...register('birthWeightKg')} />
              <Input label="Length (cm)" {...register('lengthCm')} />
              <Input label="Head circumference (cm)" {...register('ofcCm')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={ShieldAlert} title="Parents" description="Link to mother's MRN if already registered, otherwise capture details." />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input label="Mother's name" {...register('motherName')} />
              <Input label="Mother's mobile" {...register('motherMobile')} />
              <Input label="Mother's national ID" {...register('motherNationalId')} />
              <Input label="Father's name" {...register('fatherName')} />
              <Input label="Father's mobile" {...register('fatherMobile')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={ShieldAlert} title="Legal guardian (required)" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                label={<>Guardian name <span className="text-brand-600">*</span></>}
                error={errors.guardianName && 'Required'}
                {...register('guardianName')}
              />
              <Input label="Relationship to infant" placeholder="e.g. Mother, Father, Grandparent" {...register('guardianRelationship')} />
              <Input label="Guardian mobile" {...register('guardianMobile')} />
              <Input label="Guardian national ID" {...register('guardianNationalId')} />
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="p-6">
            <SectionHeader icon={Crown} title="Flags" />
            <label className="flex items-start gap-3 rounded-lg border border-brand-100 bg-brand-50/50 p-4 transition-colors hover:border-brand-200">
              <input type="checkbox" className="mt-0.5 h-4 w-4 rounded accent-brand-600" {...register('vip')} />
              <div>
                <div className="text-sm font-medium text-ink-900">VIP — bypass all payments</div>
                <div className="mt-0.5 text-xs text-ink-500">When set, all cashier steps are auto-approved for this infant.</div>
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
              {mutation.isPending ? t('common.loading') : 'Register infant'}
            </Button>
          </div>
        </div>
      </form>
    </>
  );
}
