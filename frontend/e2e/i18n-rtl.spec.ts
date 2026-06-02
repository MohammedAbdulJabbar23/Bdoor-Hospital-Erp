import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';

/**
 * Arabic / RTL smoke. The LangSwitcher (src/shared/ui/LangSwitcher.tsx) calls
 * i18n.changeLanguage('ar') on clicking the Arabic "ع" control; the i18n bootstrap
 * (src/shared/i18n/index.ts) reacts to the languageChanged event by setting
 * document.documentElement.dir = 'rtl' and lang = 'ar'.
 *
 * We log in (so the AppShell with the sidebar nav renders), click the Arabic control, then assert
 * (a) the document switches to RTL and (b) a known nav label renders in Arabic.
 */

// Arabic copy from src/shared/i18n/locales/ar.ts.
const AR_DASHBOARD = 'لوحة التحكم'; // nav.dashboard

test('switching to Arabic applies RTL and renders Arabic nav labels', async ({ page }) => {
  await login(page, 'admin');
  await page.goto('/');

  // Sanity: starts in English LTR.
  await expect(page.getByText('Dashboard', { exact: true }).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');

  // Switch to Arabic via the "ع" control in the topbar LangSwitcher.
  await page.getByRole('button', { name: 'ع', exact: true }).click();

  // (a) RTL is applied at the document root.
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl', { timeout: 10_000 });
  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  expect(await page.evaluate(() => document.dir)).toBe('rtl');

  // (b) A known nav label now renders in Arabic.
  await expect(page.getByText(AR_DASHBOARD, { exact: true }).first()).toBeVisible({ timeout: 10_000 });
});
