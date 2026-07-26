#!/usr/bin/env node
/**
 * Captures application screenshots for documentation.
 * Run from scripts/: npm install && npx playwright install chromium && node capture-screenshots.mjs
 */
import { chromium } from "playwright";
import { mkdir, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const OUT_DIR = path.join(ROOT, "docs", "screenshots");
const API = "http://localhost:8080/api";
const APP = "http://127.0.0.1:5173";
const PROJECT_ID = "8c0c0dee-dd8e-4419-bef3-a2e93c10a726";
const AUTH_STORAGE_KEY = "banking.auth.session";
const VIEWPORT = { width: 1440, height: 900 };

const DEMO_USER = process.env.DEMO_USERNAME ?? "supervisor";
const DEMO_PASS = process.env.DEMO_PASSWORD ?? "Password123!";

async function apiLogin(username, password) {
  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  return res.json();
}

async function apiGet(token, urlPath) {
  const res = await fetch(`${API}${urlPath}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`GET ${urlPath} failed: ${res.status}`);
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
  return res.status === 204 ? {} : res.json().catch(() => ({}));
}

async function findBestInvestigation(token) {
  const list = await apiGet(
    token,
    `/projects/${PROJECT_ID}/investigations`,
  );
  const items = Array.isArray(list) ? list : (list.content ?? []);
  const preferred = items.find(
    (item) =>
      ["AWAITING_REVIEW", "ASSIGNED", "IN_REVIEW"].includes(item.status),
  );
  return preferred ?? items[0] ?? null;
}

async function seedDemoData(token) {
  await apiPost(token, "/simulation/start");
  await apiPost(token, "/simulation/demos/structuring");
  await apiPost(token, "/simulation/demos/high-risk-wire");
  for (let i = 0; i < 40; i++) {
    const inv = await findBestInvestigation(token);
    if (inv) {
      try {
        const findings = await apiGet(token, `/investigations/${inv.id}/findings`);
        if ((findings?.length ?? 0) > 0) return inv;
      } catch {
        /* still executing */
      }
    }
    await new Promise((r) => setTimeout(r, 3000));
  }
  return findBestInvestigation(token);
}

async function capture(page, name) {
  await page.screenshot({ path: path.join(OUT_DIR, name), fullPage: false });
  console.log("Captured", name);
}

async function injectSession(page, loginResponse) {
  const session = {
    token: loginResponse.accessToken,
    user: {
      id: loginResponse.userId,
      username: loginResponse.username,
      role: loginResponse.role,
    },
  };
  await page.goto(`${APP}/login`, { waitUntil: "domcontentloaded" });
  await page.evaluate(
    ({ key, value }) => localStorage.setItem(key, value),
    { key: AUTH_STORAGE_KEY, value: JSON.stringify(session) },
  );
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });

  const loginResponse = await apiLogin(DEMO_USER, DEMO_PASS);
  let investigation = await findBestInvestigation(loginResponse.accessToken);
  try {
    investigation = (await seedDemoData(loginResponse.accessToken)) ?? investigation;
  } catch (err) {
    console.warn("Demo seed skipped:", err.message);
  }
  if (investigation) {
    console.log("Investigation:", investigation.id, investigation.status);
  }

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: VIEWPORT });
  const page = await context.newPage();

  await page.goto(`${APP}/login`, { waitUntil: "networkidle" });
  await page.getByLabel("Username").fill("supervisor");
  await page.getByLabel("Password").fill("");
  await capture(page, "01-login.png");

  await injectSession(page, loginResponse);
  await page.goto(`${APP}/`, { waitUntil: "networkidle" });
  await page.waitForTimeout(2500);
  await capture(page, "02-dashboard.png");

  await page.goto(`${APP}/transactions/live`, { waitUntil: "networkidle" });
  await page.waitForTimeout(3000);
  await capture(page, "03-live-transactions.png");

  await page.goto(`${APP}/transactions/suspicious`, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);
  await capture(page, "04-screening-results.png");

  await page.goto(`${APP}/investigations`, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);
  await capture(page, "05-investigations.png");

  if (investigation?.id) {
    await page.goto(`${APP}/investigations/${investigation.id}`, {
      waitUntil: "networkidle",
    });
    await page.waitForTimeout(3000);
    await capture(page, "06-investigation-command-center.png");

    const findingsTab = page.getByRole("tab", { name: /findings/i });
    if (await findingsTab.count()) {
      await findingsTab.click();
      await page.waitForTimeout(1500);
    }
    await capture(page, "07-agent-findings.png");

    await page.goto(`${APP}/investigations/${investigation.id}/explainability`, {
      waitUntil: "networkidle",
    });
    await page.waitForTimeout(2000);
    await capture(page, "08-explainability.png");

    await page.goto(`${APP}/investigations/${investigation.id}`, {
      waitUntil: "networkidle",
    });
    const reportTab = page.getByRole("tab", { name: /report/i });
    if (await reportTab.count()) {
      await reportTab.click();
      await page.waitForTimeout(2000);
    }
    await capture(page, "09-ai-report.png");

    await page.goto(`${APP}/investigations/${investigation.id}/review`, {
      waitUntil: "networkidle",
    });
    await page.waitForTimeout(2000);
    await capture(page, "13-analyst-review.png");
  }

  await page.goto(`${APP}/analyst-queue`, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);
  await capture(page, "10-analyst-queue.png");

  await page.goto(`${APP}/notifications`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  await capture(page, "11-notifications.png");

  await page.goto(`${APP}/operations`, { waitUntil: "networkidle" });
  await page.waitForTimeout(2000);
  await capture(page, "12-operations-center.png");

  await browser.close();

  const files = (await readdir(OUT_DIR)).filter((f) => f.endsWith(".png"));
  await writeFile(
    path.join(OUT_DIR, "manifest.json"),
    JSON.stringify({ captured: files.sort() }, null, 2),
  );
  console.log("Done:", files.length, "screenshots");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
