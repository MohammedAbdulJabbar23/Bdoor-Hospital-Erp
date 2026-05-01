import { SelectHTMLAttributes, forwardRef, ReactNode } from 'react';
import { cn } from './cn';

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label?: ReactNode;
  error?: ReactNode;
};

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, className, id, children, ...rest }, ref) => {
    const selectId = id ?? rest.name;
    return (
      <div className="flex flex-col gap-1">
        {label && (
          <label htmlFor={selectId} className="text-sm font-medium text-ink-700">
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          className={cn(
            'h-10 rounded-lg border bg-white px-3 text-sm text-ink-900',
            'focus:border-brand-500',
            error ? 'border-brand-600' : 'border-ink-200',
            className,
          )}
          {...rest}
        >
          {children}
        </select>
        {error && <p className="text-xs text-brand-700">{error}</p>}
      </div>
    );
  },
);
Select.displayName = 'Select';
