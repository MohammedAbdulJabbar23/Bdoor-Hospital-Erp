import { useTranslation } from 'react-i18next';
import { cn } from './cn';

export function LangSwitcher({ inverse = false }: { inverse?: boolean }) {
  const { i18n } = useTranslation();
  const current = i18n.language.startsWith('ar') ? 'ar' : 'en';

  const baseBtn = 'h-7 rounded-md px-2 text-xs font-medium transition-colors';
  const activeCls = inverse ? 'bg-white text-brand-700' : 'bg-brand-600 text-white';
  const inactiveCls = inverse
    ? 'text-brand-100 hover:bg-brand-700'
    : 'text-ink-600 hover:bg-ink-100';

  return (
    <div className="inline-flex items-center gap-1 rounded-lg border border-transparent p-0.5">
      <button
        type="button"
        onClick={() => i18n.changeLanguage('en')}
        className={cn(baseBtn, current === 'en' ? activeCls : inactiveCls)}
      >
        EN
      </button>
      <button
        type="button"
        onClick={() => i18n.changeLanguage('ar')}
        className={cn(baseBtn, current === 'ar' ? activeCls : inactiveCls)}
      >
        ع
      </button>
    </div>
  );
}
