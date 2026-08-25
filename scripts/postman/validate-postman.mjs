#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  "..",
  "..",
);
const defaultCollections = [
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend.postman_collection.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend-user-flows.postman_collection.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend-websocket-manual-integration.postman_collection.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "collections",
    "chat-backend-websocket-participants-down.postman_collection.json",
  ),
];
const defaultEnvironmentPaths = [
  path.join(
    repoRoot,
    "postman",
    "environments",
    "local.example.postman_environment.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "environments",
    "devdocker.example.postman_environment.json",
  ),
  path.join(
    repoRoot,
    "postman",
    "environments",
    "production.example.postman_environment.json",
  ),
];
const collectionPaths = process.argv[2]
  ? [path.resolve(process.argv[2])]
  : defaultCollections;
const environmentPaths =
  process.argv.length > 3
    ? process.argv.slice(3).map((arg) => path.resolve(arg))
    : defaultEnvironmentPaths;
const openApiPath = path.join(repoRoot, "docs", "api", "openapi.json");

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

function collectOperations(items, out = new Set()) {
  for (const item of items ?? []) {
    if (item.item) {
      collectOperations(item.item, out);
      continue;
    }
    const method = String(item?.request?.method || "").toUpperCase();
    const raw = String(item?.request?.url?.raw || "").split("?", 1)[0];
    const requestPath = raw
      .replace(/^\{\{base_url\}\}/, "")
      .replace(/\{\{[^}]+\}\}/g, "{param}")
      .replace(/\{[^}]+\}/g, "{param}")
      .replace(/\/+$/, "");
    if (method && requestPath) {
      out.add(`${method} ${requestPath}`);
    }
  }
  return out;
}

function collectOperationExampleCoverage(items, out = new Map()) {
  for (const item of items ?? []) {
    if (item.item) {
      collectOperationExampleCoverage(item.item, out);
      continue;
    }
    const operations = collectOperations([item]);
    const operation = operations.values().next().value;
    if (!operation) {
      continue;
    }
    const coverage = out.get(operation) ?? { success: false, failure: false };
    for (const response of item.response ?? []) {
      const status = Number(response?.code);
      coverage.success ||= status >= 200 && status < 400;
      coverage.failure ||= status >= 400;
    }
    out.set(operation, coverage);
  }
  return out;
}

function openApiOperations(document) {
  const operations = new Set();
  for (const [endpointPath, pathItem] of Object.entries(document.paths ?? {})) {
    const canonicalPath = endpointPath.replace(/\{[^}]+\}/g, "{param}");
    for (const method of ["get", "post", "put", "delete", "patch"]) {
      if (pathItem?.[method]) {
        operations.add(`${method.toUpperCase()} ${canonicalPath}`);
      }
    }
  }
  return operations;
}

function collectRunAllSmokeViolations(
  items,
  folderTrail = [],
  violations = [],
) {
  for (const item of items ?? []) {
    if (item.item) {
      collectRunAllSmokeViolations(
        item.item,
        [...folderTrail, String(item.name || "")],
        violations,
      );
      continue;
    }

    if (!folderTrail.includes("Run-all API smoke journey")) {
      continue;
    }

    const requestName = String(item.name || "<unnamed-request>");
    const testEvent = (item.event ?? []).find(
      (event) =>
        event?.listen === "test" &&
        Array.isArray(event?.script?.exec) &&
        event.script.exec.length > 0,
    );

    if (!testEvent) {
      violations.push(
        `${requestName}: missing test event script in Run-all API smoke journey`,
      );
      continue;
    }

    const scriptLines = testEvent.script.exec.map((line) => String(line));
    const pmTestCalls = scriptLines.filter((line) => line.includes("pm.test("));
    if (pmTestCalls.length === 0) {
      violations.push(
        `${requestName}: missing pm.test assertion in Run-all API smoke journey`,
      );
    }

    const hasExpectedNaming = pmTestCalls.some((line) =>
      /pm\.test\((['"])Expected:/.test(line),
    );
    if (!hasExpectedNaming) {
      violations.push(
        `${requestName}: test name must start with 'Expected:' in Run-all API smoke journey`,
      );
    }

    const hasStatusAssertion = scriptLines.some(
      (line) =>
        /pm\.response\.to\.have\.status\(/.test(line) ||
        /pm\.expect\(\[[^\]]+\]\)\.to\.include\(pm\.response\.code\)/.test(
          line,
        ),
    );
    if (!hasStatusAssertion) {
      violations.push(
        `${requestName}: missing explicit HTTP status assertion in Run-all API smoke journey`,
      );
    }
  }

  return violations;
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
  const openApi = readJson(openApiPath);
  const environments = environmentPaths.map((environmentPath) => ({
    path: environmentPath,
    content: readJson(environmentPath),
  }));

  for (const { path: environmentPath, content: environment } of environments) {
    if (!environment.name) {
      fail(`Environment name is required in ${environmentPath}`);
    }
    if (!Array.isArray(environment.values)) {
      fail(`Environment values array is required in ${environmentPath}`);
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
  }

  for (const collectionPath of collectionPaths) {
    const collection = readJson(collectionPath);

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
        `Duplicate or invalid request names found in ${path.relative(repoRoot, collectionPath)}: ${Array.from(duplicateNames).join(", ")}`,
      );
    }

    const collectionVars = new Set(
      (collection.variable ?? []).map((v) => v.key),
    );
    const allStrings = collectStrings(collection);
    const usedVars = extractVariables(allStrings);

    for (const {
      path: environmentPath,
      content: environment,
    } of environments) {
      const environmentVars = new Set(
        (environment.values ?? []).map((v) => v.key),
      );
      const knownVars = new Set([...collectionVars, ...environmentVars]);
      const unresolved = Array.from(usedVars).filter(
        (v) => !knownVars.has(v) && !v.startsWith("$"),
      );
      if (unresolved.length > 0) {
        fail(
          `Unresolved collection variables in ${path.relative(repoRoot, collectionPath)} against ${path.relative(repoRoot, environmentPath)}: ${unresolved.join(", ")}`,
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
        fail(
          `Secret-like tokens detected in ${path.relative(repoRoot, collectionPath)} when paired with ${path.relative(repoRoot, environmentPath)}.`,
        );
      }
    }

    const requestUrls = collectRequestUrlRaw(collection.item);
    const hardcodedHttp = requestUrls.filter(
      (s) => /https?:\/\//i.test(s) && !s.includes("{{base_url}}"),
    );
    if (hardcodedHttp.length > 0) {
      fail(
        `Hardcoded absolute URLs detected in ${path.relative(repoRoot, collectionPath)}. Use {{base_url}}.`,
      );
    }

    if (
      collectionPath.endsWith("chat-backend-user-flows.postman_collection.json")
    ) {
      const runAllViolations = collectRunAllSmokeViolations(collection.item);
      if (runAllViolations.length > 0) {
        fail(
          `Run-all smoke validation failed in ${path.relative(repoRoot, collectionPath)}: ${runAllViolations.join("; ")}`,
        );
      }
    }
  }

  const requiredHealthUrls = [
    "{{base_url}}/q/health/live",
    "{{base_url}}/q/health/ready",
  ];
  const hasHealthUrls = collectionPaths.some((collectionPath) => {
    const collection = readJson(collectionPath);
    const requestUrls = collectRequestUrlRaw(collection.item);
    return requiredHealthUrls.every((url) => requestUrls.includes(url));
  });
  if (!hasHealthUrls) {
    fail(
      `Missing required Quarkus health request URLs: ${requiredHealthUrls.join(", ")}. Run ./scripts/postman/discover-postman.sh to restore protected health checks.`,
    );
  }

  const mainCollection = readJson(defaultCollections[0]);
  const collectionOperations = collectOperations(mainCollection.item);
  const expectedOperations = openApiOperations(openApi);
  const missingOperations = Array.from(expectedOperations).filter(
    (operation) => !collectionOperations.has(operation),
  );
  if (missingOperations.length > 0) {
    fail(
      `Main Postman collection is missing OpenAPI operations: ${missingOperations.join(", ")}`,
    );
  }
  const exampleCoverage = collectOperationExampleCoverage(mainCollection.item);
  const incompleteExamples = Array.from(expectedOperations).filter((operation) => {
    const coverage = exampleCoverage.get(operation);
    return !coverage?.success || !coverage?.failure;
  });
  if (incompleteExamples.length > 0) {
    fail(
      `Main Postman collection needs positive and negative examples for: ${incompleteExamples.join(", ")}`,
    );
  }

  if (!process.exitCode) {
    const collectionNames = collectionPaths
      .map((collectionPath) => path.relative(repoRoot, collectionPath))
      .join(", ");
    const environmentNames = environmentPaths
      .map((environmentPath) => path.relative(repoRoot, environmentPath))
      .join(", ");
    console.log(
      `Postman validation passed for ${collectionNames} with environments: ${environmentNames}`,
    );
  }
} catch (error) {
  fail(error instanceof Error ? error.message : String(error));
}
