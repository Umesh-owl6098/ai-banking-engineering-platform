#!/usr/bin/env node
/**
 * @deprecated Use capture-demo-screenshots.mjs
 */
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const script = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  "capture-demo-screenshots.mjs",
);

const result = spawnSync(process.execPath, [script], {
  stdio: "inherit",
  env: process.env,
});

process.exit(result.status ?? 1);
