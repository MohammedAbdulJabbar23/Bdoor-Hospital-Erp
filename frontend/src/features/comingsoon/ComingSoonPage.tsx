import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Construction, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import { findNavItem } from '@/shared/nav/routes';
import { Button } from '@/shared/ui/Button';
import { Card, CardBody } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';

export function ComingSoonPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const match = findNavItem(location.pathname);
  const Icon = match?.item.icon ?? Construction;
  const moduleName = match ? t(match.item.i18nKey) : t('comingSoon.title');

  return (
    <Card className="overflow-hidden">
      <CardBody className="grid grid-cols-1 items-center gap-8 p-10 lg:grid-cols-2 lg:p-14">
        <div className="space-y-5">
          <Badge tone="brand">{t('comingSoon.title')}</Badge>
          <h1 className="text-3xl font-semibold tracking-tight text-ink-900">
            {moduleName}
          </h1>
          <p className="max-w-md text-sm leading-relaxed text-ink-600">
            {t('comingSoon.body')}
          </p>
          <div className="pt-2">
            <Link to="/">
              <Button variant="primary">
                <ArrowLeft size={16} className="me-2 rtl:rotate-180" />
                {t('comingSoon.backHome')}
              </Button>
            </Link>
          </div>
        </div>
        <div className="flex items-center justify-center">
          <div className="relative">
            <div className="absolute -inset-8 rounded-full bg-gradient-to-br from-brand-50 via-white to-brand-50 blur-2xl" />
            <div className="relative flex h-48 w-48 items-center justify-center rounded-full bg-gradient-to-br from-brand-600 to-brand-800 text-white shadow-elevated">
              <Icon size={72} strokeWidth={1.4} />
            </div>
          </div>
        </div>
      </CardBody>
    </Card>
  );
}
