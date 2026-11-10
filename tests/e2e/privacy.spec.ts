import { test, expect, type Browser } from "@playwright/test";

const baseURL = process.env.SERA_E2E_BASE_URL;
const ownerId = process.env.SERA_E2E_OWNER_ID;
const connected = { email: process.env.SERA_E2E_CONNECTED_EMAIL, password: process.env.SERA_E2E_CONNECTED_PASSWORD };
const unrelated = { email: process.env.SERA_E2E_UNRELATED_EMAIL, password: process.env.SERA_E2E_UNRELATED_PASSWORD };
const owner = { email: process.env.SERA_E2E_OWNER_EMAIL, password: process.env.SERA_E2E_OWNER_PASSWORD };
const blocked = { email: process.env.SERA_E2E_BLOCKED_EMAIL, password: process.env.SERA_E2E_BLOCKED_PASSWORD };

test.beforeAll(() => {
  const required = ["SERA_E2E_BASE_URL", "SERA_E2E_OWNER_ID", "SERA_E2E_OWNER_EMAIL", "SERA_E2E_OWNER_PASSWORD", "SERA_E2E_CONNECTED_EMAIL", "SERA_E2E_CONNECTED_PASSWORD", "SERA_E2E_UNRELATED_EMAIL", "SERA_E2E_UNRELATED_PASSWORD", "SERA_E2E_BLOCKED_EMAIL", "SERA_E2E_BLOCKED_PASSWORD"];
  const missing = required.filter((name) => !process.env[name]);
  if (missing.length) throw new Error("Missing E2E configuration: " + missing.join(", "));
});

async function signIn(browser: Browser, credentials: { email?: string; password?: string }) {
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  await page.goto("/sign-in");
  // The login form must expose accessible email and password fields.
  await page.getByRole("textbox", { name: /email/i }).fill(credentials.email!);
  await page.locator('input[type="password"]').fill(credentials.password!);
  await page.getByRole("button", { name: /sign in|log in/i }).click();
  await expect(page).not.toHaveURL(/\/signin(?:\?|$)/, { timeout: 15000 });
  return { context, page };
}

test("anonymous visitor cannot open a private member profile", async ({ page }) => {
  await page.goto(`/members/${ownerId}`);
  await expect(page).not.toHaveURL(new RegExp(`/members/${ownerId}$`));
});

test("anonymous visitor cannot view unpublished public portfolio", async ({ page }) => {
  // The owner fixture must have an unpublished portfolio.
  await page.goto(`/portfolio/${ownerId}`);
  await expect(page.getByRole("heading", { name: /404|not found/i })).toBeVisible();
});

test("owner can access their settings and own profile", async ({ browser }) => {
  const { context, page } = await signIn(browser, owner);
  try {
    await page.goto("/settings");
    await expect(page.getByRole("heading", { name: /professional profile visibility/i })).toBeVisible();
    await page.goto(`/members/${ownerId}`);
    await expect(page.getByRole("link", { name: /edit private profile/i })).toBeVisible();
  } finally { await context.close(); }
});

test("connected professional can access connections-only profile", async ({ browser }) => {
  const { context, page } = await signIn(browser, connected);
  try {
    await page.goto(`/members/${ownerId}`);
    await expect(page.getByText(/SERA PROFESSIONAL COMMUNITY/i)).toBeVisible();
  } finally { await context.close(); }
});

test("unrelated professional cannot open connections-only profile", async ({ browser }) => {
  const { context, page } = await signIn(browser, unrelated);
  try {
    await page.goto(`/members/${ownerId}`);
    await expect(page.getByRole("heading", { name: /404|not found/i })).toBeVisible();
  } finally { await context.close(); }
});

test("blocked professional cannot open member profile", async ({ browser }) => {
  const { context, page } = await signIn(browser, blocked);
  try {
    await page.goto(`/members/${ownerId}`);
    await expect(page.getByRole("heading", { name: /404|not found/i })).toBeVisible();
  } finally { await context.close(); }
});
