#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const __dirname = path.dirname(new URL(import.meta.url).pathname);
const repoRoot = path.resolve(__dirname, "..", "..");
const defaultConfigPath = path.join(repoRoot, "postman", "config.properties");

function fail(msg) {
  console.error(`ERROR: ${msg}`);
  process.exit(1);
}

function parseProperties(content) {
  const result = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const eq = trimmed.indexOf("=");
    if (eq <= 0) {
      continue;
    }
    result[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return result;
}

function loadApiKey(configPath) {
  const props = fs.existsSync(configPath)
    ? parseProperties(fs.readFileSync(configPath, "utf8"))
    : {};
  return (
    process.env.POSTMAN_API_KEY ||
    props["postman-api-key"] ||
    props["POSTMAN_API_KEY"] ||
    ""
  );
}

async function request(apiKey, pathSuffix) {
  const response = await fetch(`https://api.getpostman.com${pathSuffix}`, {
    headers: {
      "X-Api-Key": apiKey,
    },
  });
  if (!response.ok) {
    fail(`Postman API request failed (${response.status}) at ${pathSuffix}`);
  }
  return response.json();
}

(async () => {
  const configPath = process.env.POSTMAN_CONFIG_FILE
    ? path.resolve(process.env.POSTMAN_CONFIG_FILE)
    : defaultConfigPath;

  const apiKey = loadApiKey(configPath);
  if (!apiKey) {
    fail(
      "Missing API key. Set POSTMAN_API_KEY or postman-api-key in local ignored config.",
    );
  }

  const workspaces = (await request(apiKey, "/workspaces")).workspaces || [];
  if (workspaces.length === 0) {
    console.log("No workspaces available.");
    return;
  }

  for (const ws of workspaces) {
    const workspace = (
      await request(apiKey, `/workspaces/${encodeURIComponent(ws.id)}`)
    ).workspace;
    const collections = workspace.collections || [];
    const environments = workspace.environments || [];

    console.log(
      `\nWorkspace: ${workspace.name} | id=${workspace.id} | type=${workspace.type}`,
    );
    console.log(`Collections (${collections.length}):`);
    for (const c of collections) {
      console.log(`- ${c.name} | id=${c.id}`);
    }
    console.log(`Environments (${environments.length}):`);
    for (const e of environments) {
      console.log(`- ${e.name} | id=${e.id}`);
    }
  }
})();
