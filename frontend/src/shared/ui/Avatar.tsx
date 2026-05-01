import { cn } from './cn';

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

export function Avatar({
  name,
  size = 32,
  className,
}: {
  name: string;
  size?: number;
  className?: string;
}) {
  return (
    <span
      style={{ width: size, height: size, fontSize: Math.max(11, size * 0.4) }}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full bg-brand-600 font-semibold text-white',
        className,
      )}
    >
      {initials(name)}
    </span>
  );
}
