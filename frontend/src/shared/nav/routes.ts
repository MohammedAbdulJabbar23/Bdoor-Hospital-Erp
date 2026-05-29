import {
  LayoutDashboard,
  Users,
  CalendarDays,
  ListOrdered,
  FlaskConical,
  Scan,
  HeartPulse,
  Siren,
  Baby,
  BedDouble,
  Pill,
  Wallet,
  ShieldCheck,
  BookOpen,
  Settings,
  Stethoscope,
  CalendarCheck,
  type LucideIcon,
} from 'lucide-react';
import type { Role } from '../auth/authStore';

export type NavItem = {
  to: string;
  i18nKey: string;
  icon: LucideIcon;
  comingSoon?: boolean;
  /** When set, only users with at least one of these roles see the item. */
  roles?: Role[];
};

export type NavGroup = {
  i18nKey: string;
  items: NavItem[];
};

export const navGroups: NavGroup[] = [
  {
    i18nKey: 'nav.group.main',
    items: [
      { to: '/', i18nKey: 'nav.dashboard', icon: LayoutDashboard },
    ],
  },
  {
    i18nKey: 'nav.group.reception',
    items: [
      { to: '/reception/patients', i18nKey: 'nav.patients', icon: Users },
      { to: '/reception/appointments', i18nKey: 'nav.appointments', icon: CalendarDays },
      { to: '/reception/queue', i18nKey: 'nav.queue', icon: ListOrdered },
    ],
  },
  {
    i18nKey: 'nav.group.clinical',
    items: [
      { to: '/my-schedule', i18nKey: 'nav.mySchedule', icon: CalendarCheck, roles: ['DOCTOR'] },
    ],
  },
  {
    i18nKey: 'nav.group.departments',
    items: [
      { to: '/departments/laboratory', i18nKey: 'nav.laboratory', icon: FlaskConical },
      { to: '/departments/radiology', i18nKey: 'nav.radiology', icon: Scan },
      { to: '/departments/eco', i18nKey: 'nav.eco', icon: HeartPulse },
      { to: '/departments/emergency', i18nKey: 'nav.emergency', icon: Siren, comingSoon: true },
      { to: '/departments/premature', i18nKey: 'nav.premature', icon: Baby, roles: ['PREMATURE_STAFF', 'ADMIN', 'NURSE', 'DOCTOR'] },
    ],
  },
  {
    i18nKey: 'nav.group.services',
    items: [
      { to: '/pharmacy', i18nKey: 'nav.pharmacy', icon: Pill, roles: ['PHARMACIST', 'ADMIN'] },
      { to: '/pharmacy/inventory', i18nKey: 'nav.pharmacyInventory', icon: BookOpen, roles: ['PHARMACIST', 'ADMIN'] },
      { to: '/cashier', i18nKey: 'nav.cashier', icon: Wallet },
    ],
  },
  {
    i18nKey: 'nav.group.admin',
    items: [
      { to: '/admin/doctors', i18nKey: 'nav.doctors', icon: Stethoscope, roles: ['ADMIN'] },
      { to: '/admin/users', i18nKey: 'nav.users', icon: ShieldCheck, roles: ['ADMIN'] },
      { to: '/admin/catalogues', i18nKey: 'nav.catalogues', icon: BookOpen, roles: ['ADMIN'] },
      { to: '/premature/beds', i18nKey: 'nav.prematureBeds', icon: BedDouble, roles: ['ADMIN', 'PREMATURE_STAFF'] },
      { to: '/admin/settings', i18nKey: 'nav.settings', icon: Settings, comingSoon: true, roles: ['ADMIN'] },
    ],
  },
];

/** Filter nav groups + items by the user's roles. Items without role restriction are visible to everyone. */
export function visibleNav(roles: Role[]): NavGroup[] {
  const set = new Set(roles);
  return navGroups
    .map((g) => ({
      ...g,
      items: g.items.filter((it) => !it.roles || it.roles.some((r) => set.has(r))),
    }))
    .filter((g) => g.items.length > 0);
}

/** Look up nav item metadata for the active route, used by breadcrumbs/title. */
export function findNavItem(pathname: string): { group: NavGroup; item: NavItem } | null {
  for (const group of navGroups) {
    for (const item of group.items) {
      if (pathname === item.to || (item.to !== '/' && pathname.startsWith(item.to + '/'))) {
        return { group, item };
      }
    }
  }
  return null;
}
