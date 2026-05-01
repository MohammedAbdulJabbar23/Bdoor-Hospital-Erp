import { HTMLAttributes } from 'react';
import { cn } from './cn';

type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info';

const TONES: Record<Tone, string> = {
  neutral: 'bg-ink-100 text-ink-700 ring-ink-200',
  brand: 'bg-brand-50 text-brand-700 ring-brand-200',
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  warning: 'bg-amber-50 text-amber-800 ring-amber-200',
  danger: 'bg-brand-100 text-brand-800 ring-brand-300',
  info: 'bg-sky-50 text-sky-700 ring-sky-200',
};

type Props = HTMLAttributes<HTMLSpanElement> & {
  tone?: Tone;
  dot?: boolean;
};

export function Badge({ tone = 'neutral', dot = false, className, children, ...rest }: Props) {
  return (
    <span
      className={cn(
        'inline-flex h-5 items-center gap-1.5 rounded-full px-2 text-xs font-medium ring-1 ring-inset',
        TONES[tone],
        className,
      )}
      {...rest}
    >
      {dot && (
        <span
          className={cn(
            'h-1.5 w-1.5 rounded-full',
            tone === 'success' && 'bg-emerald-500',
            tone === 'warning' && 'bg-amber-500',
            tone === 'danger' && 'bg-brand-600',
            tone === 'brand' && 'bg-brand-600',
            tone === 'info' && 'bg-sky-500',
            tone === 'neutral' && 'bg-ink-400',
          )}
        />
      )}
      {children}
    </span>
  );
}
