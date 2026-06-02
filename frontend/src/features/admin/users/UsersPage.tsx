import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Plus, ShieldCheck, X, Users as UsersIcon } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Input } from '@/shared/ui/Input';
import { Avatar } from '@/shared/ui/Avatar';
import { extractApiError } from '@/shared/api/client';
import { cn } from '@/shared/ui/cn';
import { listUsers, createUser, Role } from './api';

const ALL_ROLES: Role[] = [
  'ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE', 'CASHIER',
  'LAB_STAFF', 'RADIOLOGY_STAFF', 'ECO_STAFF',
  'EMERGENCY_STAFF', 'PREMATURE_STAFF', 'PHARMACIST',
];

const schema = z.object({
  username: z.string().min(3).max(100),
  password: z.string().min(6).max(100),
  fullName: z.string().min(1).max(200),
});
type FormValues = z.infer<typeof schema>;

export function UsersPage() {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const { data: users, isLoading } = useQuery({ queryKey: ['users'], queryFn: listUsers });

  const dt = new Intl.DateTimeFormat(i18n.language === 'ar' ? 'ar-IQ' : 'en-GB', {
    day: '2-digit', month: 'short', year: 'numeric',
  });

  return (
    <>
      <PageHeader
        title={t('users.title')}
        description={t('users.description')}
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus size={14} className="me-1.5" />
            {t('users.newUser')}
          </Button>
        }
      />

      <Card>
        {isLoading ? (
          <div className="space-y-2 p-4">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : !users || users.length === 0 ? (
          <EmptyState icon={UsersIcon} title={t('users.noUsers')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-ink-100 bg-ink-50/60 text-[11px] font-semibold uppercase tracking-wide text-ink-500">
                <tr>
                  <Th>{t('users.colUser')}</Th>
                  <Th>{t('users.colUsername')}</Th>
                  <Th>{t('users.colRoles')}</Th>
                  <Th>{t('users.colStatus')}</Th>
                  <Th>{t('users.colCreated')}</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {users.map((u) => (
                  <tr key={u.id} className="group hover:bg-ink-50/60">
                    <Td>
                      <div className="flex items-center gap-2">
                        <Avatar name={u.fullName} size={28} />
                        <span className="font-medium text-ink-900">{u.fullName}</span>
                      </div>
                    </Td>
                    <Td>
                      <span className="font-mono text-xs text-ink-700">@{u.username}</span>
                    </Td>
                    <Td>
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <span key={r} className="rounded bg-ink-100 px-1.5 py-0.5 text-[10px] font-medium text-ink-700">{r}</span>
                        ))}
                      </div>
                    </Td>
                    <Td>
                      {u.active ? <Badge tone="success" dot>{t('users.active')}</Badge> : <Badge tone="neutral" dot>{t('users.disabled')}</Badge>}
                    </Td>
                    <Td><span className="text-xs text-ink-700">{dt.format(new Date(u.createdAt))}</span></Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {open && (
        <CreateUserDialog
          onClose={() => setOpen(false)}
          onCreated={async () => {
            await queryClient.invalidateQueries({ queryKey: ['users'] });
            setOpen(false);
          }}
        />
      )}
    </>
  );
}

function CreateUserDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => Promise<void> }) {
  const { t } = useTranslation();
  const [selectedRoles, setSelectedRoles] = useState<Set<Role>>(new Set(['RECEPTIONIST']));
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      createUser({ ...values, roles: Array.from(selectedRoles) }),
    onSuccess: async (u) => {
      toast.success(t('users.created', { username: u.username }));
      await onCreated();
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? t('users.createFailed')),
  });

  const toggleRole = (r: Role) => {
    const next = new Set(selectedRoles);
    if (next.has(r)) next.delete(r); else next.add(r);
    setSelectedRoles(next);
  };

  const onSubmit = handleSubmit((v) => {
    if (selectedRoles.size === 0) {
      toast.error(t('users.pickRole'));
      return;
    }
    mutation.mutate(v);
  });

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-ink-900/50 p-4 backdrop-blur-sm">
      <div className="w-full max-w-xl overflow-hidden rounded-xl bg-white shadow-elevated">
        <div className="flex items-center justify-between border-b border-ink-100 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-ink-900">{t('users.dialogTitle')}</h2>
            <p className="mt-0.5 text-xs text-ink-500">{t('users.dialogSubtitle')}</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-ink-500 hover:bg-ink-100">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4 p-5">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input label={t('users.username')} autoComplete="off" error={errors.username && t('users.usernameError')} {...register('username')} />
            <Input label={t('users.password')} type="text" autoComplete="off" error={errors.password && t('users.passwordError')} {...register('password')} />
            <div className="sm:col-span-2">
              <Input label={t('users.fullName')} error={errors.fullName && t('users.fullNameError')} {...register('fullName')} />
            </div>
          </div>

          <div>
            <label className="mb-2 flex items-center gap-1.5 text-sm font-medium text-ink-700">
              <ShieldCheck size={14} className="text-brand-600" /> {t('users.roles')}
            </label>
            <div className="flex flex-wrap gap-2">
              {ALL_ROLES.map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => toggleRole(r)}
                  className={cn(
                    'inline-flex h-7 items-center rounded-full px-3 text-xs font-medium transition-colors',
                    selectedRoles.has(r)
                      ? 'bg-brand-600 text-white'
                      : 'border border-ink-200 bg-white text-ink-600 hover:bg-ink-50',
                  )}
                >
                  {r}
                </button>
              ))}
            </div>
          </div>
        </form>

        <div className="flex items-center justify-end gap-2 border-t border-ink-100 bg-ink-50/40 px-5 py-3">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="button" onClick={onSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? t('users.creating') : t('users.createUser')}
          </Button>
        </div>
      </div>
    </div>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn('px-4 py-3 text-start font-semibold', className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 align-middle', className)}>{children}</td>;
}
