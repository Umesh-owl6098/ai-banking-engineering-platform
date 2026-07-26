#!/usr/bin/env node
/**
 * Captures authenticated demo screenshots for documentation.
 *
 * Prerequisites:
 *   - PostgreSQL via docker compose
 *   - Backend on http://localhost:8080
 *   - Frontend on http://localhost:5173
 *
 * Usage:
 *   export DEMO_USER_PASSWORD='your-local-demo-password'
 *   cd scripts && node capture-demo-screenshots.mjs
 */
import { createHash } from "node:crypto";
import { readFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const OUT_DIR = path.join(ROOT, "docs", "screenshots");
const API = process.env.API_BASE_URL ?? "http://localhost:8080/api";
const APP = process.env.APP_BASE_URL ?? "http://localhost:5173";
const PROJECT_ID =
  process.env.VITE_PROJECT_ID ?? "8c0c0dee-dd8e-4419-bef3-a2e93c10a726";
const NAV_WAIT = "domcontentloaded";
const VIEWPORT = { width: 1440, height: 900 };

const DEMO_USER = process.env.DEMO_USERNAME ?? "supervisor";
const DEMO_PASS = process.env.DEMO_USER_PASSWORD ?? process.env.DEMO_PASSWORD;

if (!DEMO_PASS) {
  console.error(
    "Missing DEMO_USER_PASSWORD (or DEMO_PASSWORD). Set it in the environment.",
  );
  process.exit(1);
}

async function apiLogin(username, password) {
  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`API login failed: ${res.status} ${JSON.stringify(body)}`);
  }
  return body;
}

async function apiGet(token, urlPath) {
  const res = await fetch(`${API}${urlPath}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error(`GET ${urlPath} failed: ${res.status}`);
  }
  return res.json();
}

async function apiPost(token, urlPath, body) {
  const res = await fetch(`${API}${urlPath}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok && res.status !== 409) {
    throw new Error(`POST ${urlPath} failed: ${res.status}`);
  }
  return res.json().catch(() => ({}));
}

async function findBestInvestigation(token) {
  const list = await apiGet(token, `/projects/${PROJECT_ID}/investigations`);
  const items = Array.isArray(list) ? list : (list.content ?? []);
  return (
    items.find((item) =>
      ["AWAITING_REVIEW", "ASSIGNED", "IN_REVIEW"].includes(item.status),
    ) ??
    items.find((item) => item.status === "REPORT_GENERATED") ??
    items[0] ??
    null
  );
}

async function seedDemoData(token) {
  try {
    await apiPost(token, "/simulation/start");
  } catch {
    /* already running */
  }
  await apiPost(token, "/simulation/demos/structuring");
  await apiPost(token, "/simulation/demos/high-risk-wire");
  await apiPost(token, "/simulation/demos/money-mule");

  for (let attempt = 0; attempt < 50; attempt++) {
    const inv = await findBestInvestigation(token);
    if (inv) {
      try {
        const findings = await apiGet(
          token,
          `/investigations/${inv.id}/findings`,
        );
        const report = await apiGet(
          token,
          `/investigations/${inv.id}/report`,
        );
        if ((findings?.length ?? 0) > 0 && report) {
          return inv;
        }
      } catch {
        /* pipeline still running */
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 3000));
  }
  return findBestInvestigation(token);
}

async function assertAuthenticated(page, label) {
  if (page.url().includes("/login")) {
    throw new Error(`${label}: still on login page (${page.url()})`);
  }
  const signInButton = page.getByRole("button", { name: /sign in/i });
  if (await signInButton.isVisible().catch(() => false)) {
    throw new Error(`${label}: login form is visible`);
  }
  await page.getByRole("navigation").waitFor({ state: "visible", timeout: 15000 });
}

async function loginThroughUi(page) {
  await page.goto(`${APP}/login`, { waitUntil: NAV_WAIT });
  await page.getByLabel("Username").fill(DEMO_USER);
  await page.getByLabel("Password").fill(DEMO_PASS);
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), {
    timeout: 30000,
  });
  await assertAuthenticated(page, "post-login");
}

async function capture(page, filename, options = {}) {
  const { authenticated = false, label = filename } = options;
  if (authenticated) {
    await assertAuthenticated(page, label);
  }
  await page.waitForTimeout(800);
  const target = path.join(OUT_DIR, filename);
  await page.screenshot({ path: target, fullPage: false });
  console.log(`Captured ${filename}`);
}

async function hashFile(filename) {
  const buffer = await readFile(path.join(OUT_DIR, filename));
  return createHash("sha256").update(buffer).digest("hex");
}

async function verifyScreenshotHashes() {
  const loginHash = await hashFile("01-login.png");
  const authenticatedFiles = [
    "02-dashboard.png",
    "03-live-transactions.png",
    "04-screening-results.png",
    "05-investigations.png",
    "06-investigation-command-center.png",
    "07-agent-findings.png",
    "08-explainability.png",
    "09-ai-report.png",
    "10-analyst-queue.png",
    "11-notifications.png",
    "12-operations-center.png",
    "13-analyst-review.png",
  ];

  const hashes = new Map();
  for (const file of authenticatedFiles) {
    const hash = await hashFile(file);
    if (hash === loginHash) {
      throw new Error(`${file} matches login screenshot hash`);
    }
    hashes.set(hash, [...(hashes.get(hash) ?? []), file]);
  }

  const duplicateGroups = [...hashes.entries()].filter(
    ([, files]) => files.length > 1,
  );
  if (duplicateGroups.length > 0) {
    const detail = duplicateGroups
      .map(([hash, files]) => `${files.join(", ")} (${hash.slice(0, 8)})`)
      .join("; ");
    throw new Error(`Duplicate authenticated screenshots: ${detail}`);
  }
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });

  console.log("Verifying API authentication...");
  const loginResponse = await apiLogin(DEMO_USER, DEMO_PASS);
  const token = loginResponse.accessToken;
  await apiGet(token, "/auth/me");
  await apiGet(token, `/dashboard/operations?projectId=${PROJECT_ID}`);
  console.log("API authentication OK");

  let investigation = await findBestInvestigation(token);
  console.log("Preparing demo data...");
  investigation = (await seedDemoData(token)) ?? investigation;
  if (!investigation?.id) {
    throw new Error("No investigation available for screenshots");
  }
  console.log(`Using investigation ${investigation.id} (${investigation.status})`);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: VIEWPORT });
  const page = await context.newPage();

  await page.goto(`${APP}/login`, { waitUntil: NAV_WAIT });
  await page.getByLabel("Username").fill(DEMO_USER);
  await page.getByLabel("Password").fill("");
  await capture(page, "01-login.png");

  await loginThroughUi(page);

  await page.goto(`${APP}/`, { waitUntil: NAV_WAIT });
  await page.getByText("Operations Dashboard", { exact: false }).first().waitFor({
    timeout: 20000,
  });
  await capture(page, "02-dashboard.png", { authenticated: true });

  await page.goto(`${APP}/transactions/live`, { waitUntil: NAV_WAIT });
  await page.getByText("Live Transactions", { exact: false }).first().waitFor({
    timeout: 20000,
  });
  await capture(page, "03-live-transactions.png", { authenticated: true });

  await page.goto(`${APP}/transactions/suspicious`, {
    waitUntil: NAV_WAIT,
  });
  await page.getByText("Suspicious Transactions", { exact: false })
    .first()
    .waitFor({ timeout: 20000 });
  await capture(page, "04-screening-results.png", { authenticated: true });

  await page.goto(`${APP}/investigations`, { waitUntil: NAV_WAIT });
  await page.getByText("Investigations", { exact: false }).first().waitFor({
    timeout: 20000,
  });
  await capture(page, "05-investigations.png", { authenticated: true });

  await page.goto(`${APP}/investigations/${investigation.id}`, {
    waitUntil: NAV_WAIT,
  });
  await page.getByText("Live Investigation Pipeline", { exact: false })
    .first()
    .waitFor({ timeout: 20000 });
  await capture(page, "06-investigation-command-center.png", {
    authenticated: true,
  });

  await page.getByRole("tab", { name: /^Agent Findings$/i }).click();
  await page.locator("#findings").waitFor({ state: "visible", timeout: 20000 });
  await page.waitForTimeout(1000);
  await capture(page, "07-agent-findings.png", { authenticated: true });

  await page.getByRole("tab", { name: /^Explainability$/i }).click();
  await page.locator("#explainability").waitFor({ state: "visible", timeout: 20000 });
  await page.waitForTimeout(1000);
  await capture(page, "08-explainability.png", { authenticated: true });

  await page.getByRole("tab", { name: /^Report$/i }).click();
  await page.waitForTimeout(1500);
  await capture(page, "09-ai-report.png", { authenticated: true });

  await page.goto(`${APP}/analyst-queue`, { waitUntil: NAV_WAIT });
  await page.getByText("Analyst Review Queue", { exact: false }).first().waitFor({
    timeout: 20000,
  });
  await capture(page, "10-analyst-queue.png", { authenticated: true });

  await page.goto(`${APP}/notifications`, { waitUntil: NAV_WAIT });
  await page.getByText("Notification Center", { exact: false }).first().waitFor({
    timeout: 20000,
  });
  await capture(page, "11-notifications.png", { authenticated: true });

  await page.goto(`${APP}/operations`, { waitUntil: NAV_WAIT });
  await page.getByText("Operations Center", { exact: false }).first().waitFor({
    timeout: 20000,
  });
  await capture(page, "12-operations-center.png", { authenticated: true });

  await page.goto(`${APP}/investigations/${investigation.id}`, {
    waitUntil: NAV_WAIT,
  });
  await page.getByRole("tab", { name: /^Human Review$/i }).click();
  await page.locator("#review").waitFor({ state: "visible", timeout: 20000 });
  await page.waitForTimeout(1000);
  await capture(page, "13-analyst-review.png", { authenticated: true });

  await browser.close();

  console.log("Verifying screenshot hashes...");
  await verifyScreenshotHashes();
  console.log("All screenshots captured and verified");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
