import { ReactNode } from 'react';
import { LucideIcon } from 'lucide-react';

export function SectionHeader({
  icon: Icon,
  title,
  description,
}: {
  icon?: LucideIcon;
  title: ReactNode;
  description?: ReactNode;
}) {
  return (
    <div className="mb-4 flex items-start gap-3">
      {Icon && (
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
          <Icon size={16} aria-hidden />
        </span>
      )}
      <div>
        <h3 className="text-sm font-semibold text-ink-900">{title}</h3>
        {description && <p className="mt-0.5 text-xs text-ink-500">{description}</p>}
      </div>
    </div>
  );
}
