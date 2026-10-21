import { test, expect, Page } from '@playwright/test';

// ─── helpers ─────────────────────────────────────────────────────────────────

async function login(page: Page, username: string, password = 'ChangeMe123!') {
  await page.goto('/');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  // Wait for the app section to become visible
  await expect(page.locator('#app-view')).toBeVisible({ timeout: 10_000 });
}

async function goToApprovals(page: Page) {
  await page.getByRole('tab', { name: 'Approvals' }).click();
  await expect(page.locator('#approvals-panel')).toBeVisible();
}

// ─── E2E test: Manager is rejected when approving a > $50k invoice ────────────

test('Manager approval of over-limit invoice is rejected with clear error message', async ({ page }) => {
  // Sign in as a Manager
  await login(page, 'demo.manager');
  await goToApprovals(page);

  // INV-1006 is seeded with $75,000 — above the $50,000 Manager limit
  const invoiceItem = page.locator('[data-invoice-id="INV-1006"]');
  await expect(invoiceItem).toBeVisible({ timeout: 5_000 });

  // Click Approve — expect it to be rejected
  await invoiceItem.getByRole('button', { name: /Approve INV-1006/i }).click();

  // The error banner must appear and mention the $50,000 limit
  const errorBanner = page.locator('#decision-error');
  await expect(errorBanner).toBeVisible({ timeout: 5_000 });
  await expect(errorBanner).toContainText('50000');

  // The invoice must still be listed as PENDING_APPROVAL (not changed)
  await page.getByRole('tab', { name: 'Invoices' }).click();
  const statusCell = page.locator('#invoices-table tbody')
    .locator('tr', { hasText: 'INV-1006' })
    .locator('.status-PENDING_APPROVAL');
  await expect(statusCell).toBeVisible();
});

// ─── E2E test: Director is rejected on > $500k without second approver ────────

test('Director approval of > $500k invoice sets first approver and stays pending', async ({ page }) => {
  await login(page, 'demo.director');
  await goToApprovals(page);

  // INV-1007 is seeded with $750,000 — above the Director solo limit
  const invoiceItem = page.locator('[data-invoice-id="INV-1007"]');
  await expect(invoiceItem).toBeVisible({ timeout: 5_000 });

  await invoiceItem.getByRole('button', { name: /Approve INV-1007/i }).click();

  // No error — first approval succeeds; invoice stays pending
  const errorBanner = page.locator('#decision-error');
  await expect(errorBanner).toBeHidden();

  // Verify invoice is still shown in the approvals panel (still pending)
  await expect(invoiceItem).toBeVisible();
});

// ─── E2E test: Same Director cannot give second approval ─────────────────────

test('Same Director attempting second approval on high-value invoice gets clear error', async ({ page }) => {
  // First: sign in as director and give first approval on INV-1008
  await login(page, 'demo.director');
  await goToApprovals(page);

  const inv = page.locator('[data-invoice-id="INV-1008"]');
  await inv.getByRole('button', { name: /Approve INV-1008/i }).click();

  // First approval accepted — now try again as the SAME director
  await inv.getByRole('button', { name: /Approve INV-1008/i }).click();

  const errorBanner = page.locator('#decision-error');
  await expect(errorBanner).toBeVisible({ timeout: 5_000 });
  await expect(errorBanner).toContainText('different person');
});

// ─── E2E test: Manager cannot be second approver on > $500k ──────────────────

test('Manager attempting second approval on high-value invoice gets role-error', async ({ page }) => {
  // 1. Give first approval as director on INV-1007 (may already have one from previous test run — that is OK, idempotent)
  await login(page, 'demo.director');
  await goToApprovals(page);
  const inv1007 = page.locator('[data-invoice-id="INV-1007"]');
  if (await inv1007.isVisible()) {
    const approveBtn = inv1007.getByRole('button', { name: /Approve INV-1007/i });
    if (await approveBtn.isVisible()) {
      await approveBtn.click();
      // Accept either success or "already has first approver"
      await page.waitForTimeout(500);
    }
  }

  // 2. Sign out and sign in as Manager
  await page.getByRole('button', { name: 'Sign out' }).click();
  await login(page, 'demo.manager');
  await goToApprovals(page);

  const inv = page.locator('[data-invoice-id="INV-1007"]');
  if (await inv.isVisible()) {
    await inv.getByRole('button', { name: /Approve INV-1007/i }).click();

    const errorBanner = page.locator('#decision-error');
    await expect(errorBanner).toBeVisible({ timeout: 5_000 });
    await expect(errorBanner).toContainText('Director');
  }
});

// ─── E2E test: Reject does not require any amount check ──────────────────────

test('Manager can reject a > $500k invoice with a reason — no approval-limit error', async ({ page }) => {
  await login(page, 'demo.manager');
  await goToApprovals(page);

  // Use INV-1008 which is $1,200,000 — well above any limit
  const inv = page.locator('[data-invoice-id="INV-1008"]');
  await expect(inv).toBeVisible({ timeout: 5_000 });

  await inv.getByLabel(/Rejection reason for INV-1008/i).fill('Duplicate submission — reject without limit check');
  await inv.getByRole('button', { name: /Reject INV-1008/i }).click();

  // Should succeed — no error
  const errorBanner = page.locator('#decision-error');
  await expect(errorBanner).toBeHidden();

  // Invoice should now be gone from the pending list
  await expect(inv).toBeHidden();
});
