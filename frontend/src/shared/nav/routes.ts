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
  Pill,
  Wallet,
  ShieldCheck,
  BookOpen,
  Settings,
  type LucideIcon,
} from 'lucide-react';

export type NavItem = {
  to: string;
  i18nKey: string;
  icon: LucideIcon;
  comingSoon?: boolean;
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
      { to: '/reception/appointments', i18nKey: 'nav.appointments', icon: CalendarDays, comingSoon: true },
      { to: '/reception/queue', i18nKey: 'nav.queue', icon: ListOrdered, comingSoon: true },
    ],
  },
  {
    i18nKey: 'nav.group.departments',
    items: [
      { to: '/departments/laboratory', i18nKey: 'nav.laboratory', icon: FlaskConical, comingSoon: true },
      { to: '/departments/radiology', i18nKey: 'nav.radiology', icon: Scan, comingSoon: true },
      { to: '/departments/eco', i18nKey: 'nav.eco', icon: HeartPulse, comingSoon: true },
      { to: '/departments/emergency', i18nKey: 'nav.emergency', icon: Siren, comingSoon: true },
      { to: '/departments/premature', i18nKey: 'nav.premature', icon: Baby, comingSoon: true },
    ],
  },
  {
    i18nKey: 'nav.group.services',
    items: [
      { to: '/pharmacy', i18nKey: 'nav.pharmacy', icon: Pill, comingSoon: true },
      { to: '/cashier', i18nKey: 'nav.cashier', icon: Wallet, comingSoon: true },
    ],
  },
  {
    i18nKey: 'nav.group.admin',
    items: [
      { to: '/admin/users', i18nKey: 'nav.users', icon: ShieldCheck, comingSoon: true },
      { to: '/admin/catalogues', i18nKey: 'nav.catalogues', icon: BookOpen, comingSoon: true },
      { to: '/admin/settings', i18nKey: 'nav.settings', icon: Settings, comingSoon: true },
    ],
  },
];

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
