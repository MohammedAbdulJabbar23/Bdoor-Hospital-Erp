import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Lock, User as UserIcon, ShieldCheck, HeartPulse, Stethoscope } from 'lucide-react';
import { api } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { LangSwitcher } from '@/shared/ui/LangSwitcher';

const schema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});
type Form = z.infer<typeof schema>;

export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.set);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (data) => {
    setServerError(null);
    try {
      const res = await api.post('/auth/login', data);
      setAuth(res.data.token, res.data.expiresAt, res.data.user);
      const redirectTo = (location.state as { from?: Location })?.from?.pathname ?? '/';
      navigate(redirectTo, { replace: true });
    } catch {
      setServerError(t('login.failed'));
    }
  });

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Brand panel */}
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-brand-800 to-brand-900 lg:flex lg:flex-col lg:justify-between lg:p-12 lg:text-white">
        <div className="absolute inset-0 opacity-10">
          <div className="absolute -top-24 -end-24 h-96 w-96 rounded-full bg-white/30 blur-3xl" />
          <div className="absolute -bottom-32 -start-24 h-96 w-96 rounded-full bg-white/20 blur-3xl" />
        </div>

        <div className="relative flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white text-brand-700 shadow-lg">
            <CrossLogo />
          </div>
          <div>
            <div className="text-lg font-semibold">{t('app.name')}</div>
            <div className="text-xs text-brand-100">{t('app.tagline')}</div>
          </div>
        </div>

        <div className="relative space-y-6">
          <h2 className="text-3xl font-semibold leading-tight">
            {t('app.tagline')}
          </h2>
          <p className="max-w-md text-sm text-brand-100">
            Reception, departments, pharmacy, cashier and admin — every clinical workflow in one
            place, fully bilingual (English / العربية).
          </p>

          <div className="space-y-3 pt-2">
            <FeaturePoint icon={Stethoscope}>End-to-end patient lifecycle, from intake to discharge.</FeaturePoint>
            <FeaturePoint icon={HeartPulse}>Real-time bed dashboard, queues, and lab/radiology orders.</FeaturePoint>
            <FeaturePoint icon={ShieldCheck}>Audit trail on every state change, role-based access.</FeaturePoint>
          </div>
        </div>

        <p className="relative text-xs text-brand-200">
          © {new Date().getFullYear()} Albudoor Hospital · v0.1.0
        </p>
      </div>

      {/* Form panel */}
      <div className="flex items-center justify-center bg-ink-50 p-6">
        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center justify-between lg:hidden">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-white">
                <CrossLogo />
              </div>
              <span className="font-semibold text-ink-900">{t('app.name')}</span>
            </div>
            <LangSwitcher />
          </div>

          <div className="rounded-2xl border border-ink-200 bg-white p-8 shadow-card">
            <div className="mb-6 flex items-start justify-between">
              <div>
                <h1 className="text-2xl font-semibold tracking-tight text-ink-900">
                  {t('login.title')}
                </h1>
                <p className="mt-1 text-sm text-ink-500">{t('login.subtitle')}</p>
              </div>
              <div className="hidden lg:block">
                <LangSwitcher />
              </div>
            </div>

            <form onSubmit={onSubmit} className="space-y-4">
              <FieldWithIcon icon={UserIcon}>
                <Input
                  label={t('login.username')}
                  autoComplete="username"
                  className="ps-9"
                  error={errors.username && t('common.required')}
                  {...register('username')}
                />
              </FieldWithIcon>
              <FieldWithIcon icon={Lock}>
                <Input
                  label={t('login.password')}
                  type="password"
                  autoComplete="current-password"
                  className="ps-9"
                  error={errors.password && t('common.required')}
                  {...register('password')}
                />
              </FieldWithIcon>

              {serverError && (
                <div className="flex items-start gap-2 rounded-lg border border-brand-200 bg-brand-50 p-3 text-sm text-brand-800">
                  <span className="mt-0.5 h-2 w-2 shrink-0 rounded-full bg-brand-600" />
                  <span>{serverError}</span>
                </div>
              )}

              <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? t('common.loading') : t('login.submit')}
              </Button>
            </form>

            <p className="mt-6 border-t border-ink-100 pt-4 text-center text-xs text-ink-400">
              {t('login.devHint')}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function FieldWithIcon({
  icon: Icon,
  children,
}: {
  icon: typeof Lock;
  children: React.ReactNode;
}) {
  return (
    <div className="relative">
      <Icon
        size={14}
        className="pointer-events-none absolute start-3 top-[34px] text-ink-400"
        aria-hidden
      />
      {children}
    </div>
  );
}

function FeaturePoint({
  icon: Icon,
  children,
}: {
  icon: typeof Lock;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white/15 backdrop-blur">
        <Icon size={14} aria-hidden />
      </span>
      <p className="text-sm text-brand-50">{children}</p>
    </div>
  );
}

function CrossLogo() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden>
      <path d="M10 3h4a1 1 0 0 1 1 1v5h5a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-5v5a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-5H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1h5V4a1 1 0 0 1 1-1Z" />
    </svg>
  );
}
