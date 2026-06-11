import { useTranslation } from 'react-i18next';
import { Crown, IdCard, Phone, Calendar, MapPin } from 'lucide-react';
import { Badge } from '@/shared/ui/Badge';
import type { PatientResponse } from '@/features/reception/api';

function initials(name: string) {
  const p = name.trim().split(/\s+/).filter(Boolean);
  if (p.length === 0) return '?';
  if (p.length === 1) return p[0][0].toUpperCase();
  return (p[0][0] + p[p.length - 1][0]).toUpperCase();
}

export function computeAge(dob: string): string {
  const birth = new Date(dob);
  const now = new Date();
  let years = now.getFullYear() - birth.getFullYear();
  const m = now.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) years--;
  if (years < 1) {
    const months = (now.getFullYear() - birth.getFullYear()) * 12 + m;
    return `${months}mo`;
  }
  return `${years}y`;
}

function Chip({ icon: Icon, label, children }: { icon?: typeof Phone; label: string; children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-ink-100 bg-ink-50/60 px-2.5 py-1 text-xs">
      {Icon && <Icon size={12} className="text-ink-400" />}
      <span className="text-ink-500">{label}:</span>
      <span className="font-medium text-ink-700">{children}</span>
    </span>
  );
}

export function ProfileHeader({ patient }: { patient: PatientResponse }) {
  const { t } = useTranslation();
  const contact = patient.adult?.mobileNumber ?? patient.infant?.guardianMobile ?? null;
  const contactName = patient.adult?.mobileNumber ? null : patient.infant?.guardianName ?? null;

  return (
    <div data-testid="profile-header" className="rounded-lg border border-ink-100 bg-white p-4">
      <div className="flex items-start gap-3">
        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-50 text-lg font-semibold text-brand-700">
          {initials(patient.fullName)}
        </span>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-ink-900">{patient.fullName}</h1>
            {patient.vip && <Badge tone="brand"><Crown size={11} className="me-0.5" />VIP</Badge>}
            {patient.archived && <Badge tone="neutral">{t('patientProfile.archived')}</Badge>}
            <Badge tone={patient.type === 'INFANT' ? 'warning' : 'info'}>
              {patient.type === 'INFANT' ? t('patientProfile.infant') : t('patientProfile.adult')}
            </Badge>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Chip icon={IdCard} label={t('patientProfile.mrn')}><span className="font-mono">{patient.mrn}</span></Chip>
            <Chip label={t('patientProfile.gender')}>
              {patient.gender === 'MALE' ? t('patientProfile.male') : t('patientProfile.female')}
            </Chip>
            <Chip icon={Calendar} label={t('patientProfile.dob')}>
              {new Date(patient.dateOfBirth).toLocaleDateString()} <span className="text-ink-500">· {computeAge(patient.dateOfBirth)}</span>
            </Chip>
            {contact && (
              <Chip icon={Phone} label={t('patientProfile.mobile')}>
                <span className="font-mono">{contact}</span>
                {contactName && <span className="ms-1 text-ink-500">({contactName})</span>}
              </Chip>
            )}
          </div>
          {patient.adult?.address && (
            <div className="mt-1.5 inline-flex items-center gap-1 text-xs text-ink-500">
              <MapPin size={12} /> {patient.adult.address}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
