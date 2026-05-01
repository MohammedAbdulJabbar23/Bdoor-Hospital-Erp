import { InputHTMLAttributes, forwardRef, ReactNode } from 'react';
import { cn } from './cn';

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
};

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, hint, error, className, id, ...rest }, ref) => {
    const inputId = id ?? rest.name;
    return (
      <div className="flex flex-col gap-1">
        {label && (
          <label htmlFor={inputId} className="text-sm font-medium text-ink-700">
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={cn(
            'h-10 rounded-lg border bg-white px-3 text-sm text-ink-900 placeholder:text-ink-400',
            'focus:border-brand-500',
            error ? 'border-brand-600' : 'border-ink-200',
            className,
          )}
          {...rest}
        />
        {hint && !error && <p className="text-xs text-ink-500">{hint}</p>}
        {error && <p className="text-xs text-brand-700">{error}</p>}
      </div>
    );
  },
);
Input.displayName = 'Input';
