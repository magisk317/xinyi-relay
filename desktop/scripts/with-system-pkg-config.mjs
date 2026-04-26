#!/usr/bin/env node

import { execFileSync, spawn } from "node:child_process";
import { existsSync } from "node:fs";

const args = process.argv.slice(2);

if (args.length === 0) {
  console.error("Usage: with-system-pkg-config <command> [args...]");
  process.exit(1);
}

const env = { ...process.env };

if (process.platform === "linux" && existsSync("/usr/bin/pkg-config")) {
  let systemPcPath = "";
  try {
    systemPcPath = execFileSync(
      "/usr/bin/pkg-config",
      ["--variable", "pc_path", "pkg-config"],
      { encoding: "utf8" },
    ).trim();
  } catch {
    systemPcPath = "";
  }

  env.TAURI_LINUX_AYATANA_APPINDICATOR =
    env.TAURI_LINUX_AYATANA_APPINDICATOR || "1";
  env.PATH = `/usr/bin:${env.PATH || ""}`;
  env.PKG_CONFIG = "/usr/bin/pkg-config";

  if (systemPcPath) {
    env.PKG_CONFIG_PATH = env.PKG_CONFIG_PATH
      ? `${systemPcPath}:${env.PKG_CONFIG_PATH}`
      : systemPcPath;
  }
}

const child = spawn(args[0], args.slice(1), {
  env,
  shell: false,
  stdio: "inherit",
});

child.on("error", (error) => {
  console.error(error.message);
  process.exit(1);
});

child.on("exit", (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
