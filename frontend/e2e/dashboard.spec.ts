import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { API_BASE } from './helpers/seeds';

/**
 * The dashboard KPIs were previously hardcoded stub values (24 / 6 / 12-20 / 4 + fake activity).
 * This asserts they now render REAL data from GET /api/dashboard/summary, and that the
 * recent-activity list shows the honest empty state (no fabricated samples).
 */
test('dashboard renders real metrics from /api/dashboard/summary', async ({ page }) => {
  const admin = await authedContext('admin');
  const summary = await (await admin.get(`${API_BASE}/dashboard/summary`)).json();
  await admin.dispose();

  await login(page, 'admin');
  await page.goto('/');

  // Bed-occupancy tile renders the live "occupied / total" from the API (unique string format).
  await expect(
    page.getByText(`${summary.bedsOccupied} / ${summary.bedsTotal}`, { exact: false }),
  ).toBeVisible({ timeout: 15_000 });

  // Honest empty state replaced the fake "Layla Hassan / Dr. Kareem" activity samples.
  await expect(page.getByText(/No recent activity yet/i)).toBeVisible();

  // The old stub footnote is gone.
  await expect(page.getByText(/Live metrics will populate once/i)).toHaveCount(0);
});
