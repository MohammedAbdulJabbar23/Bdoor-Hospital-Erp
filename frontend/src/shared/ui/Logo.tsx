import { useTranslation } from 'react-i18next';

export function Logo({ inverse = false }: { inverse?: boolean }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-3">
      <div
        className={
          inverse
            ? 'flex h-9 w-9 items-center justify-center rounded-lg bg-white text-brand-600'
            : 'flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-white'
        }
        aria-hidden
      >
        <CrossMark />
      </div>
      <div className="leading-tight">
        <div className={inverse ? 'text-base font-semibold text-white' : 'text-base font-semibold text-ink-900'}>
          {t('app.name')}
        </div>
        <div className={inverse ? 'text-xs text-brand-100' : 'text-xs text-ink-500'}>
          {t('app.tagline')}
        </div>
      </div>
    </div>
  );
}

function CrossMark() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden>
      <path d="M10 3h4a1 1 0 0 1 1 1v5h5a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-5v5a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-5H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1h5V4a1 1 0 0 1 1-1Z" />
    </svg>
  );
}
