import { expect, test, type Page } from '@playwright/test';

const password = process.env.E2E_ADMIN_PASSWORD ?? '';

async function signIn(page: Page, pw = password) {
  await page.goto('/sign-in');
  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill(pw);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('a wrong password is refused with a message', async ({ page }) => {
  await signIn(page, 'not-the-password');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/sign-in/);
});

test('signing in opens tonight at the depot, and signing out returns to sign-in', async ({ page }) => {
  await signIn(page);
  await expect(page.getByRole('heading', { name: 'Tonight at the depot' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});

test('planning tonight shows the timeline, who misses departure, and the other strategies', async ({ page }) => {
  await signIn(page);
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Plan charging' }).click();
  await expect(page.getByRole('heading', { name: 'Vehicles tonight' })).toBeVisible();
  await page.getByRole('button', { name: 'Show plan' }).click();

  await expect(page.getByRole('heading', { name: /the night ahead/ })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Charging sessions per charger over the night' })).toBeVisible();
  // The NACS van has no compatible charger in the seeded depot, so it must be reported, not silently dropped.
  await expect(page.getByRole('heading', { name: 'Vehicles that will not reach their target' })).toBeVisible();
  await expect(page.getByText('No charger with a matching connector')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Same vehicles, other strategies' })).toBeVisible();
});

test('a charger failure can be simulated', async ({ page }) => {
  await signIn(page);
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Plan charging' }).click();
  await page.getByRole('button', { name: 'Show plan' }).click();
  await expect(page.getByRole('heading', { name: 'What if a charger fails?' })).toBeVisible();
  await page.getByRole('combobox', { name: /^Charger/ }).selectOption('A1');
  await page.getByRole('button', { name: 'Simulate failure' }).click();
  await expect(page.getByText(/Re-planned from/)).toBeVisible();
});

test('the advisor answers from the depot documents and shows its sources', async ({ page }) => {
  await signIn(page);
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Advisor' }).click();
  await page.getByLabel('Question').fill('When is the off-peak tariff period?');
  await page.getByRole('button', { name: 'Ask' }).click();
  await expect(page.getByText(/Time-of-Use Tariff Guide/).first()).toBeVisible();
});
