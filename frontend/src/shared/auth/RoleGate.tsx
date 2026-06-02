import { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { ShieldAlert, ArrowLeft } from 'lucide-react';
import { useAuthStore, type Role } from './authStore';
import { EmptyState } from '@/shared/ui/EmptyState';
import { PageHeader } from '@/shared/ui/PageHeader';
import { Button } from '@/shared/ui/Button';

/**
 * Restricts a route to the given roles. ADMIN implicitly passes every gate.
 * Renders a friendly "no access" view when the user has none of the allowed roles.
 */
export function RoleGate({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { t } = useTranslation();
  const userRoles = useAuthStore((s) => s.user?.roles) ?? [];

  const allowed =
    userRoles.includes('ADMIN') || roles.some((r) => userRoles.includes(r));

  if (allowed) {
    return <>{children}</>;
  }

  return (
    <div>
      <PageHeader title={t('roleGate.title')} />
      <EmptyState
        icon={ShieldAlert}
        title={t('roleGate.body')}
        description={t('roleGate.hint')}
        action={
          <Link to="/">
            <Button variant="primary">
              <ArrowLeft size={16} className="me-2 rtl:rotate-180" />
              {t('roleGate.back')}
            </Button>
          </Link>
        }
      />
    </div>
  );
}
