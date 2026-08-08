#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  "..",
  "..",
);
const collectionPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(
      repoRoot,
      "postman",
      "collections",
      "chat-backend.postman_collection.json",
    );
const environmentPath = process.argv[3]
  ? path.resolve(process.argv[3])
  : path.join(
      repoRoot,
      "postman",
      "environments",
      "local.example.postman_environment.json",
    );

function fail(msg) {
  console.error(`ERROR: ${msg}`);
  process.exitCode = 1;
}

function readJson(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`File not found: ${filePath}`);
  }
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function collectRequestNames(items, seen = new Set(), duplicates = new Set()) {
  for (const item of items ?? []) {
    if (item.item) {
      collectRequestNames(item.item, seen, duplicates);
      continue;
    }
    const name = item.name?.trim();
    if (!name) {
      duplicates.add("<unnamed-request>");
      continue;
    }
    if (seen.has(name)) {
      duplicates.add(name);
    }
    seen.add(name);
  }
  return duplicates;
}

function collectStrings(value, out = []) {
  if (typeof value === "string") {
    out.push(value);
    return out;
  }
  if (Array.isArray(value)) {
    value.forEach((v) => collectStrings(v, out));
    return out;
  }
  if (value && typeof value === "object") {
    Object.values(value).forEach((v) => collectStrings(v, out));
  }
  return out;
}

function collectRequestUrlRaw(items, out = []) {
  for (const item of items ?? []) {
    if (item.item) {
      collectRequestUrlRaw(item.item, out);
      continue;
    }
    const raw = item?.request?.url?.raw;
    if (typeof raw === "string") {
      out.push(raw);
    }
  }
  return out;
}

function extractVariables(strings) {
  const vars = new Set();
  const regex = /{{\s*([a-zA-Z0-9_.\-$]+)\s*}}/g;
  for (const s of strings) {
    let match;
    while ((match = regex.exec(s)) !== null) {
      vars.add(match[1]);
    }
  }
  return vars;
}

function isPlaceholder(value) {
  return /<[^>]+>|\{\{[^}]+\}\}|CHANGE_ME|example|placeholder/i.test(value);
}

try {
  const collection = readJson(collectionPath);
  const environment = readJson(environmentPath);

  if (!collection.info?.name) {
    fail(`Collection info.name is required in ${collectionPath}`);
  }
  if (!Array.isArray(collection.item) || collection.item.length === 0) {
    fail(
      `Collection must contain at least one request item in ${collectionPath}`,
    );
  }

  const duplicateNames = collectRequestNames(collection.item);
  if (duplicateNames.size > 0) {
    fail(
      `Duplicate or invalid request names found: ${Array.from(duplicateNames).join(", ")}`,
    );
  }

  if (!environment.name) {
    fail(`Environment name is required in ${environmentPath}`);
  }
  if (!Array.isArray(environment.values)) {
    fail(`Environment values array is required in ${environmentPath}`);
  }

  const collectionVars = new Set((collection.variable ?? []).map((v) => v.key));
  const environmentVars = new Set((environment.values ?? []).map((v) => v.key));
  const knownVars = new Set([...collectionVars, ...environmentVars]);

  const allStrings = collectStrings(collection);
  const usedVars = extractVariables(allStrings);
  const unresolved = Array.from(usedVars).filter(
    (v) => !knownVars.has(v) && !v.startsWith("$"),
  );
  if (unresolved.length > 0) {
    fail(`Unresolved collection variables: ${unresolved.join(", ")}`);
  }

  const requestUrls = collectRequestUrlRaw(collection.item);
  const hardcodedHttp = requestUrls.filter(
    (s) => /https?:\/\//i.test(s) && !s.includes("{{base_url}}"),
  );
  if (hardcodedHttp.length > 0) {
    fail("Hardcoded absolute URLs detected in collection. Use {{base_url}}.");
  }

  const requiredHealthUrls = [
    "{{base_url}}/q/health/live",
    "{{base_url}}/q/health/ready",
  ];
  const missingHealthUrls = requiredHealthUrls.filter(
    (url) => !requestUrls.includes(url),
  );
  if (missingHealthUrls.length > 0) {
    fail(
      `Missing required Quarkus health request URLs: ${missingHealthUrls.join(", ")}. Run ./scripts/postman/discover-postman.sh to restore protected health checks.`,
    );
  }

  const serializedArtifacts = `${JSON.stringify(collection)}\n${JSON.stringify(environment)}`;
  const secretMarkers = [
    /PMAK-[A-Za-z0-9-]+/i,
    /AIza[0-9A-Za-z\-_]{10,}/,
    /xox[baprs]-[0-9A-Za-z-]{10,}/,
    /-----BEGIN (?:RSA |EC )?PRIVATE KEY-----/,
  ];
  if (secretMarkers.some((r) => r.test(serializedArtifacts))) {
    fail("Secret-like tokens detected in Postman artifacts.");
  }

  for (const entry of environment.values) {
    const key = String(entry.key || "");
    const value = String(entry.value || "");
    if (
      /secret|token|password|api[_-]?key/i.test(key) &&
      value &&
      !isPlaceholder(value)
    ) {
      fail(
        `Sensitive-looking environment key '${key}' must use placeholder value.`,
      );
    }
  }

  if (!process.exitCode) {
    console.log(
      `Postman validation passed for ${path.relative(repoRoot, collectionPath)} and ${path.relative(repoRoot, environmentPath)}`,
    );
  }
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
}
