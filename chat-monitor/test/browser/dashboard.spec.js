import { expect, test } from '@playwright/test';

test('dashboard switches between independently collected environments', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('ChatMonitor');
  await expect(page.getByRole('heading', { level: 1, name: 'ChatMonitor' })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('Current');
  const selector = page.getByLabel('Viewing environment');
  await expect(selector).toHaveValue('local-dev');
  await expect(page.getByText('Local development', { exact: true })).toBeVisible();
  await expect(page.getByText('local', { exact: true })).toBeVisible();
  await expect(page.locator('body')).toHaveAttribute('data-environment-classification', 'local');
  const localCanvas = await page.locator('body').evaluate(
    (element) => element.ownerDocument.defaultView.getComputedStyle(element).backgroundColor
  );
  expect(localCanvas).toBe('rgb(7, 17, 23)');
  await expect(page.getByText('Loopback only')).toBeVisible();
  await expect(page.locator('[data-event-count]')).toHaveText('2');
  await expect(page.getByRole('heading', { name: 'Request overview' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Operations' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Sanitized failures' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Alert state' })).toBeVisible();
  await selector.focus();
  await expect(selector).toBeFocused();

  await selector.selectOption('staging-rehearsal');
  await expect(page.getByText('Staging rehearsal', { exact: true })).toBeVisible();
  await expect(page.getByText('staging', { exact: true })).toBeVisible();
  await expect(page.locator('body')).toHaveAttribute('data-environment-classification', 'staging');
  const stagingCanvas = await page.locator('body').evaluate(
    (element) => element.ownerDocument.defaultView.getComputedStyle(element).backgroundColor
  );
  expect(stagingCanvas).toBe('rgb(7, 17, 31)');
  await expect(page).toHaveURL(/environment=staging-rehearsal/);

  await selector.selectOption('production');
  await expect(page.getByText('Production', { exact: true })).toBeVisible();
  await expect(page.getByText('production', { exact: true })).toBeVisible();
  await expect(page.getByRole('status')).toContainText('Source unavailable');
  await expect(page.getByRole('status')).toContainText('Production is not deployed yet');
  await expect(page.getByText('Production is registered, but collection will begin only after the environment is deployed and onboarded.')).toBeVisible();
  await expect(page.locator('body')).toHaveAttribute('data-environment-classification', 'production');
  const productionCanvas = await page.locator('body').evaluate(
    (element) => element.ownerDocument.defaultView.getComputedStyle(element).backgroundColor
  );
  expect(productionCanvas).toBe('rgb(23, 18, 7)');
  await expect(page).toHaveURL(/environment=production/);
  await expect(page.locator('[data-event-count]')).toHaveText('0');
  await expect(page.getByText('No alerts observed.')).toBeVisible();
});
