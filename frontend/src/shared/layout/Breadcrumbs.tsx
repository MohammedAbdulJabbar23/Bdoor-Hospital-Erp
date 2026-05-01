import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ChevronRight, Home } from 'lucide-react';
import { findNavItem } from '../nav/routes';

export function Breadcrumbs() {
  const location = useLocation();
  const { t } = useTranslation();
  const match = findNavItem(location.pathname);

  if (!match || match.item.to === '/') return null;

  return (
    <nav aria-label="breadcrumb" className="flex items-center gap-1.5 text-xs text-ink-500">
      <Link to="/" className="inline-flex items-center gap-1 hover:text-ink-900">
        <Home size={12} />
        {t('nav.dashboard')}
      </Link>
      <ChevronRight size={12} className="text-ink-300 rtl:rotate-180" />
      <span>{t(match.group.i18nKey)}</span>
      <ChevronRight size={12} className="text-ink-300 rtl:rotate-180" />
      <span className="font-medium text-ink-700">{t(match.item.i18nKey)}</span>
    </nav>
  );
}
