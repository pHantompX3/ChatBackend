#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const __dirname = path.dirname(new URL(import.meta.url).pathname);
const repoRoot = path.resolve(__dirname, "..", "..");

const javaRoot = path.join(repoRoot, "src", "main", "java");
const apiRoutesPath = path.join(
  repoRoot,
  "src",
  "main",
  "java",
  "com",
  "wayden",
  "messenger",
  "common",
  "api",
  "ApiRoutes.java",
);
const collectionPath = path.join(
  repoRoot,
  "postman",
  "collections",
  "chat-backend.postman_collection.json",
);

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    fail(`Unable to parse JSON file ${filePath}`);
  }
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function listJavaFiles(dirPath, out = []) {
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const entryPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      listJavaFiles(entryPath, out);
      continue;
    }
    if (entry.isFile() && entry.name.endsWith(".java")) {
      out.push(entryPath);
    }
  }
  return out;
}

function parseApiV1Constant() {
  const fallback = "/api/v1";
  if (!fs.existsSync(apiRoutesPath)) {
    return fallback;
  }
  const content = fs.readFileSync(apiRoutesPath, "utf8");
  const match = content.match(/API_V1\s*=\s*"([^"]+)"/);
  return match ? match[1] : fallback;
}

function decodePathExpression(expr, apiV1) {
  if (!expr) {
    return "";
  }
  const compact = expr.replace(/\s+/g, " ").trim();

  if (/^"[^"]*"$/.test(compact)) {
    return compact.slice(1, -1);
  }
  if (compact === "ApiRoutes.API_V1") {
    return apiV1;
  }
  const concatMatch = compact.match(/^ApiRoutes\.API_V1\s*\+\s*"([^"]*)"$/);
  if (concatMatch) {
    return `${apiV1}${concatMatch[1]}`;
  }

  return "";
}

function normalizePath(pathValue) {
  const trimmed = String(pathValue || "").trim();
  if (!trimmed) {
    return "";
  }
  const withLeadingSlash = trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
  return withLeadingSlash.replace(/\/+/g, "/");
}

function joinPaths(basePath, methodPath) {
  return normalizePath(
    `${normalizePath(basePath)}/${normalizePath(methodPath)}`,
  );
}

function extractDiscoveredEndpoints(apiV1) {
  const discovered = [];
  const javaFiles = listJavaFiles(javaRoot);
  const routeRegex =
    /@(GET|POST|PUT|DELETE|PATCH)\b(?:[\s\S]*?@Path\(([^)]+)\))?/g;

  for (const filePath of javaFiles) {
    const content = fs.readFileSync(filePath, "utf8");
    const classPathMatch = content.match(
      /@Path\(([^)]+)\)[\s\S]*?public\s+class\s+/,
    );
    const classPathExpr = classPathMatch ? classPathMatch[1] : "";
    const classPath = decodePathExpression(classPathExpr, apiV1);

    let match;
    while ((match = routeRegex.exec(content)) !== null) {
      const method = match[1];
      const methodPathExpr = match[2] || "";
      const methodPath = decodePathExpression(methodPathExpr, apiV1);
      const fullPath = joinPaths(classPath, methodPath);
      if (!fullPath) {
        continue;
      }

      discovered.push({
        method,
        path: fullPath,
        source: path.relative(repoRoot, filePath),
        sourceGroup: path.basename(filePath, ".java"),
      });
    }
  }

  const uniqueByKey = new Map();
  for (const endpoint of discovered) {
    uniqueByKey.set(`${endpoint.method} ${endpoint.path}`, endpoint);
  }

  return Array.from(uniqueByKey.values()).sort((a, b) => {
    const left = `${a.path} ${a.method}`;
    const right = `${b.path} ${b.method}`;
    return left.localeCompare(right);
  });
}

function findFolder(collection, folderName) {
  const items = Array.isArray(collection.item) ? collection.item : [];
  return items.find(
    (entry) => entry?.name === folderName && Array.isArray(entry?.item),
  );
}

function ensureFolder(collection, folderName, description) {
  if (!Array.isArray(collection.item)) {
    collection.item = [];
  }
  let folder = findFolder(collection, folderName);
  if (!folder) {
    folder = { name: folderName, description, item: [] };
    collection.item.push(folder);
  } else if (!Array.isArray(folder.item)) {
    folder.item = [];
  }
  return folder;
}

function ensureChildFolder(parentFolder, folderName, description) {
  if (!Array.isArray(parentFolder.item)) {
    parentFolder.item = [];
  }
  let folder = parentFolder.item.find(
    (entry) => entry?.name === folderName && Array.isArray(entry?.item),
  );
  if (!folder) {
    folder = { name: folderName, description, item: [] };
    parentFolder.item.push(folder);
  } else if (!Array.isArray(folder.item)) {
    folder.item = [];
  }
  return folder;
}

function collectRequests(collection, out = []) {
  for (const item of collection.item ?? []) {
    if (Array.isArray(item?.item)) {
      collectRequests(item, out);
      continue;
    }
    if (item?.request?.method && item?.request?.url?.raw) {
      out.push(item);
    }
  }
  return out;
}

function toCollectionPathParam(segment) {
  const match = segment.match(/^\{([a-zA-Z0-9_]+)\}$/);
  if (!match) {
    return segment;
  }
  const name = match[1].replace(/([a-z0-9])([A-Z])/g, "$1_$2").toLowerCase();
  return `{{${name}}}`;
}

function buildRawUrl(urlPath) {
  const pieces = urlPath
    .split("/")
    .filter(Boolean)
    .map((segment) => toCollectionPathParam(segment));
  return `{{base_url}}/${pieces.join("/")}`;
}

function buildPathArray(urlPath) {
  return urlPath
    .split("/")
    .filter(Boolean)
    .map((segment) => toCollectionPathParam(segment));
}

function canonicalizeRawUrl(rawUrl) {
  const text = String(rawUrl || "").trim();
  if (!text) {
    return "";
  }
  const withoutQuery = text.split("?")[0];
  return withoutQuery.replace(/\/+$/, "");
}

function requestKey(method, rawUrl) {
  return `${String(method || "").toUpperCase()} ${canonicalizeRawUrl(rawUrl)}`;
}

function extractPathFromRawUrl(rawUrl) {
  const value = String(rawUrl || "").trim();
  if (!value) {
    return "";
  }
  const withoutQuery = value.split("?")[0];
  const withoutBase = withoutQuery.replace(/^\{\{base_url\}\}/, "");
  return normalizePath(withoutBase);
}

function inferDomainFolderByPath(pathValue) {
  const normalized = normalizePath(pathValue);
  if (!normalized) {
    return "";
  }
  if (normalized === "/api/v1/ping" || normalized.startsWith("/q/health")) {
    return "Health";
  }
  if (
    normalized.startsWith("/api/v1/messages") ||
    /\/api\/v1\/conversations\/[^/]+\/messages$/.test(normalized)
  ) {
    return "Messaging";
  }
  return "";
}

function extractSourceGroupFromRequest(entry) {
  const description = String(entry?.request?.description || "");
  const match = description.match(/Auto-discovered from .*\/([^/]+)\.java\.?$/);
  if (match?.[1]) {
    return match[1];
  }
  return "Misc";
}

function buildDefaultEvent(method) {
  return [
    {
      listen: "test",
      script: {
        type: "text/javascript",
        exec: [
          "pm.test('Status code is 200 or 201', function () {",
          "  pm.expect([200, 201]).to.include(pm.response.code);",
          "});",
          "",
          "pm.test('Response is JSON when body is present', function () {",
          "  if (pm.response.text().trim().length > 0) {",
          "    pm.response.to.be.json;",
          "  }",
          "});",
        ],
      },
    },
  ];
}

function buildDiscoveredRequest(endpoint) {
  const isWrite =
    endpoint.method === "POST" ||
    endpoint.method === "PUT" ||
    endpoint.method === "PATCH";

  const request = {
    auth: { type: "noauth" },
    method: endpoint.method,
    header: [{ key: "Accept", value: "application/json" }],
    url: {
      raw: buildRawUrl(endpoint.path),
      host: ["{{base_url}}"],
      path: buildPathArray(endpoint.path),
    },
    description: `Auto-discovered from ${endpoint.source}.`,
  };

  if (isWrite) {
    request.header.unshift({ key: "Content-Type", value: "application/json" });
    request.body = {
      mode: "raw",
      raw: "{}",
      options: {
        raw: { language: "json" },
      },
    };
  }

  return {
    name: `${endpoint.method} ${endpoint.path}`,
    request,
    response: [
      {
        name: "Example",
        originalRequest: {
          method: endpoint.method,
          header: request.header,
          body: request.body,
          url: request.url,
        },
        status: "OK",
        code: endpoint.method === "POST" ? 201 : 200,
        _postman_previewlanguage: "json",
        header: [{ key: "Content-Type", value: "application/json" }],
        body: "{}",
      },
    ],
    event: buildDefaultEvent(endpoint.method),
  };
}

function buildHealthTemplate(name, rawPath, description, testName) {
  const pathArray = rawPath.split("/").filter(Boolean);
  return {
    name,
    request: {
      auth: { type: "noauth" },
      method: "GET",
      header: [{ key: "Accept", value: "application/json" }],
      url: {
        raw: `{{base_url}}${rawPath}`,
        host: ["{{base_url}}"],
        path: pathArray,
      },
      description,
    },
    response: [
      {
        name: "200 UP",
        originalRequest: {
          method: "GET",
          header: [{ key: "Accept", value: "application/json" }],
          url: {
            raw: `{{base_url}}${rawPath}`,
            host: ["{{base_url}}"],
            path: pathArray,
          },
        },
        status: "OK",
        code: 200,
        _postman_previewlanguage: "json",
        header: [{ key: "Content-Type", value: "application/json" }],
        body: '{\n  "status": "UP"\n}',
      },
    ],
    event: [
      {
        listen: "test",
        script: {
          type: "text/javascript",
          exec: [
            "pm.test('Status code is 200', function () {",
            "  pm.response.to.have.status(200);",
            "});",
            "",
            `pm.test('${testName}', function () {`,
            "  var jsonData = pm.response.json();",
            "  pm.expect(jsonData).to.have.property('status', 'UP');",
            "});",
          ],
        },
      },
    ],
  };
}

function ensureProtectedHealthRequests(collection) {
  const healthFolder = ensureFolder(
    collection,
    "Health",
    "Application and platform health endpoints for reachability and readiness.",
  );

  const protectedRequests = [
    buildHealthTemplate(
      "Health Live",
      "/q/health/live",
      "Quarkus liveness endpoint. Indicates process is alive.",
      "Health live is UP",
    ),
    buildHealthTemplate(
      "Health Ready",
      "/q/health/ready",
      "Quarkus readiness endpoint. Includes DB connectivity readiness checks.",
      "Health ready is UP (DB readiness)",
    ),
    buildHealthTemplate(
      "Health",
      "/q/health",
      "Quarkus aggregate health endpoint (includes readiness/liveness summary).",
      "Aggregate health is UP",
    ),
  ];

  const existingByRawUrl = new Map(
    (healthFolder.item ?? [])
      .filter((entry) => entry?.request?.url?.raw)
      .map((entry) => [entry.request.url.raw, entry]),
  );

  let addedCount = 0;
  for (const protectedRequest of protectedRequests) {
    const key = protectedRequest.request.url.raw;
    if (!existingByRawUrl.has(key)) {
      healthFolder.item.push(protectedRequest);
      addedCount += 1;
    }
  }

  return addedCount;
}

function mergeDiscoveredEndpoints(collection, endpoints) {
  const legacyFolder = findFolder(collection, "Discovered APIs");

  const nonDiscoveredRequests = [];
  for (const topLevelItem of collection.item ?? []) {
    if (topLevelItem === legacyFolder) {
      continue;
    }
    collectRequests(topLevelItem, nonDiscoveredRequests);
  }

  const nonDiscoveredKeys = new Set(
    nonDiscoveredRequests.map((entry) =>
      requestKey(entry?.request?.method, entry?.request?.url?.raw),
    ),
  );

  const existingDiscoveredRequests = legacyFolder
    ? collectRequests(legacyFolder)
    : [];
  const reusableRequests = existingDiscoveredRequests.filter((entry) => {
    if (!entry?.request?.method || !entry?.request?.url?.raw) {
      return false;
    }
    return !nonDiscoveredKeys.has(
      requestKey(entry.request.method, entry.request.url.raw),
    );
  });

  let cleanedCount =
    existingDiscoveredRequests.length - reusableRequests.length;

  const reusableByKey = new Map(
    reusableRequests.map((entry) => [
      requestKey(entry?.request?.method, entry?.request?.url?.raw),
      entry,
    ]),
  );

  const allRequests = collectRequests(collection);
  const existingKeys = new Set(
    allRequests.map((entry) =>
      requestKey(entry?.request?.method, entry?.request?.url?.raw),
    ),
  );

  let addedCount = 0;
  let movedCount = 0;
  const grouped = new Map();

  const addToGroup = (groupName, requestItem) => {
    if (!grouped.has(groupName)) {
      grouped.set(groupName, []);
    }
    grouped.get(groupName).push(requestItem);
  };

  for (const [key, entry] of reusableByKey.entries()) {
    const domainFolderName = inferDomainFolderByPath(
      extractPathFromRawUrl(entry?.request?.url?.raw),
    );
    if (domainFolderName) {
      const domainFolder = ensureFolder(collection, domainFolderName, "");
      domainFolder.item.push(entry);
      movedCount += 1;
      existingKeys.add(key);
      continue;
    }

    const groupName = extractSourceGroupFromRequest(entry);
    addToGroup(groupName, entry);
    existingKeys.add(key);
  }

  for (const endpoint of endpoints) {
    const rawUrl = buildRawUrl(endpoint.path);
    const key = requestKey(endpoint.method, rawUrl);
    if (existingKeys.has(key)) {
      continue;
    }
    const requestItem = buildDiscoveredRequest(endpoint);
    const domainFolderName = inferDomainFolderByPath(endpoint.path);
    if (domainFolderName) {
      const domainFolder = ensureFolder(collection, domainFolderName, "");
      domainFolder.item.push(requestItem);
    } else {
      addToGroup(endpoint.sourceGroup || "Misc", requestItem);
    }
    existingKeys.add(key);
    addedCount += 1;
  }

  for (const groupName of Array.from(grouped.keys()).sort((a, b) =>
    a.localeCompare(b),
  )) {
    const groupFolder = ensureFolder(
      collection,
      groupName,
      `Auto-discovered endpoints from ${groupName}.java.`,
    );
    groupFolder.item = grouped.get(groupName);
  }

  if (legacyFolder && (collection.item ?? []).includes(legacyFolder)) {
    cleanedCount += 1;
    collection.item = (collection.item ?? []).filter(
      (item) => item !== legacyFolder,
    );
  }

  return { addedCount, movedCount, cleanedCount };
}

function run() {
  if (!fs.existsSync(collectionPath)) {
    fail(`Collection file not found: ${collectionPath}`);
  }

  const collection = readJson(collectionPath);
  const apiV1 = parseApiV1Constant();
  const discoveredEndpoints = extractDiscoveredEndpoints(apiV1);

  const healthAdded = ensureProtectedHealthRequests(collection);
  const { addedCount, movedCount, cleanedCount } = mergeDiscoveredEndpoints(
    collection,
    discoveredEndpoints,
  );

  if (
    healthAdded === 0 &&
    addedCount === 0 &&
    movedCount === 0 &&
    cleanedCount === 0
  ) {
    console.log("Postman discovery completed: no collection changes needed.");
    return;
  }

  writeJson(collectionPath, collection);
  console.log(
    `Postman discovery completed: added ${healthAdded} protected health request(s), added ${addedCount} discovered API request(s), moved ${movedCount} discovered request(s) into domain folders, cleaned ${cleanedCount} duplicate/stale discovered request(s).`,
  );
}

run();
